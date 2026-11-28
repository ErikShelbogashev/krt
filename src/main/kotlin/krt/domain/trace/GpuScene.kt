package krt.domain.trace

import kotlin.math.roundToLong
import krt.domain.scene.model.Fog
import krt.domain.scene.model.Fractal
import krt.domain.scene.model.Material
import krt.domain.scene.model.Medium
import krt.domain.scene.model.MediumInterface
import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Sky
import krt.domain.scene.model.Sphere
import krt.domain.scene.model.Traceable
import krt.domain.scene.model.Translucent
import krt.domain.scene.model.Triangle
import krt.domain.scene.model.VertexNormals
import krt.platform.math.Point3

/**
 * Scene packed for the GPU shader (ADR 0009): one RGBA_8888 texture [width] texels wide, one number per texel.
 * A number is 24 bits in the R, G, B channels, alpha is always 255: a coordinate in fixed point
 * (see [GpuScenePacking.encode]) or a whole number (an index, a count, a code) as is.
 *
 * Six sections follow each other:
 * - [nodeCount] BVH nodes from texel 0, [GpuScenePacking.NODE_VALUES] each: box min x, y, z, max x, y, z,
 *   skip link, first primitive, primitive count (0 for an inner node); see [FlatBvh];
 * - [primitiveCount] primitives from [primitiveStart] in the order of the leaves,
 *   [GpuScenePacking.PRIMITIVE_VALUES] each: kind, then the centre, radius and velocity of a sphere, the first
 *   vertex and the two edges from it of a triangle or the centre, radius, kind, iterations, parameter and seed of
 *   a fractal (ADR 0015); then, for a triangle with vertex normals, 1 + the number of its record in the normals
 *   section, 0 for a flat one; last 1 + the numbers of the media inside and outside the surface, 0 for none
 *   (ADR 0014);
 * - their materials from [materialStart], [GpuScenePacking.MATERIAL_VALUES] each: material code, then a colour,
 *   two parameters, a second colour and a third parameter, as [GpuMaterials.values] lays them out;
 * - [GpuSections.lightCount] lights from [GpuSections.lightStart], [GpuScenePacking.LIGHT_VALUES] each: the
 *   cumulative share of their weights (see [Emitters]) as a whole number of 2^24, the first of their members and
 *   how many, and the area density of a point on them with their pick, log-encoded ([GpuLightTable.encodeLog]);
 *   then their members from [GpuSections.memberStart], [GpuScenePacking.MEMBER_VALUES] each: the primitive slot
 *   and the cumulative share of the light's area; light sampling picks them when [SceneLook.sampleLights] is on;
 * - from [GpuSections.normalStart], the vertex normals of smooth triangles, [GpuScenePacking.NORMAL_VALUES] per
 *   record: the normals at the first, second and third vertex (ADR 0013);
 * - from [GpuSections.mediaStart], the media, [GpuScenePacking.MEDIUM_VALUES] each: density, albedo and the mean
 *   cosine g of the phase function.
 */
class GpuScene(
    val data: ByteArray,
    val width: Int,
    val nodeCount: Int,
    val primitiveStart: Int,
    val materialStart: Int,
    val look: SceneLook = SceneLook.CLASSIC,
    val sections: GpuSections = GpuSections(materialStart, 0, materialStart, materialStart, 0, materialStart),
    val fogs: List<GpuFog> = emptyList(),
) {
    val sky: Sky get() = look.sky

    /** The lights the shader samples: all of them, or none when light sampling is off. */
    val sampledLights: Int get() = if (look.sampleLights) sections.lightCount else 0

    /** 1 when the scene has smooth triangles, whose vertex normals the shader reads, else 0. */
    val smoothNormals: Float get() = if (sections.normalCount > 0) 1f else 0f

    /** The number of the medium at [point], that of a bank of fog around it, or -1 in clear air (ADR 0014). */
    fun mediumAt(point: Point3): Int = fogs.firstOrNull { (point - it.center).length() < it.radius }?.medium ?: -1

    /** 1 when the shader sends rays at random moments of the shutter and moves the balls, else 0. */
    val motionBlur: Float get() = if (look.motionBlur) 1f else 0f

    val primitiveCount: Int get() = (materialStart - primitiveStart) / GpuScenePacking.PRIMITIVE_VALUES

    /** Rows of the texture. */
    val height: Int get() = data.size / (width * GpuScenePacking.BYTES_PER_TEXEL)
}

/**
 * Where the light slots of a [GpuScene] lie and how many there are; where the records of vertex normals after
 * them start and how many there are; and where the media start.
 */
data class GpuSections(
    val lightStart: Int,
    val lightCount: Int,
    val memberStart: Int,
    val normalStart: Int,
    val normalCount: Int,
    val mediaStart: Int,
)

/** A bank of fog of a [GpuScene]: where it is and the number of its [medium], so a camera in it starts there. */
data class GpuFog(val center: Point3, val radius: Double, val medium: Int)

/** Packing of the scene model for the GPU shader: a pure transformation, mirrored by the shader's decoding. */
object GpuScenePacking {

    const val NODE_VALUES = 9
    const val PRIMITIVE_VALUES = 14
    const val MEDIUM_VALUES = 7
    const val LIGHT_VALUES = 4
    const val MEMBER_VALUES = 2
    const val NORMAL_VALUES = 9
    const val MATERIAL_VALUES = 14
    const val TEXTURE_WIDTH = 1024

    /** Packed coordinates lie in [-RANGE, RANGE). */
    const val RANGE = 4096.0

    const val SPHERE = 0
    const val TRIANGLE = 1
    const val FRACTAL = 2

    const val LAMBERT = 0
    const val METAL = 1
    const val DIELECTRIC = 2
    const val EMISSIVE = 3
    const val PLASTIC = 4
    const val CHECKER = 5
    const val INTERFACE = 6
    const val BUBBLE = 7
    const val IRIDESCENT = 8
    const val CONDUCTOR = 9
    const val VELVET = 10
    const val PATTERNED = 11
    const val GLOWING = 12

    private const val FIXED_BITS = 24
    private const val FIXED_ONE = 1 shl FIXED_BITS
    const val BYTES_PER_TEXEL = 4
    private const val BYTE_MASK = 0xFF
    private const val OPAQUE: Byte = -1
    private const val RED = 0
    private const val GREEN = 1
    private const val BLUE = 2
    private const val ALPHA = 3
    private const val BOX_VALUES = 6

    fun pack(objects: List<Traceable>, look: SceneLook = SceneLook.CLASSIC): GpuScene {
        val primitives = objects.mapNotNull(::primitiveOf).toTypedArray()
        val media = primitives.flatMap { listOfNotNull(mediaOf(it)?.inside, mediaOf(it)?.outside) }.distinct()
        val bvh = Bvh(primitives).flatten()
        val primitiveStart = bvh.nodeCount * NODE_VALUES
        val materialStart = primitiveStart + primitives.size * PRIMITIVE_VALUES
        val lightStart = materialStart + primitives.size * MATERIAL_VALUES
        val emitters = Emitters.of(primitives.toList())
        val memberStart = lightStart + emitters.size * LIGHT_VALUES
        val normalStart = memberStart + emitters.sumOf { it.members.size } * MEMBER_VALUES
        val smooth = bvh.order.map { (primitives[it] as? Triangle)?.normals }
        val records = smooth.runningFold(0) { count, normals -> if (normals == null) count else count + 1 }
        val mediaStart = normalStart + records.last() * NORMAL_VALUES
        val codes = IntArray(mediaStart + media.size * MEDIUM_VALUES)
        for (node in 0 until bvh.nodeCount) nodeCodes(bvh, node).copyInto(codes, node * NODE_VALUES)
        bvh.order.forEachIndexed { slot, index ->
            val record = smooth[slot]?.let { records[slot] + 1 } ?: 0
            val numbers = mediaOf(primitives[index])?.let { listOf(it.inside, it.outside) }
                ?.map { medium -> media.indexOf(medium) + 1 } ?: listOf(0, 0)
            primitiveCodes(primitives[index], intArrayOf(record) + numbers)
                .copyInto(codes, primitiveStart + slot * PRIMITIVE_VALUES)
            materialCodes(primitives[index]).copyInto(codes, materialStart + slot * MATERIAL_VALUES)
            smooth[slot]?.let { normalCodes(it).copyInto(codes, normalStart + records[slot] * NORMAL_VALUES) }
        }
        GpuLightTable.codes(emitters, slotsOf(bvh, primitives)).copyInto(codes, lightStart)
        media.forEachIndexed { number, medium ->
            mediumCodes(medium).copyInto(codes, mediaStart + number * MEDIUM_VALUES)
        }
        val height = maxOf(1, (codes.size + TEXTURE_WIDTH - 1) / TEXTURE_WIDTH)
        return GpuScene(
            data = texture(codes, height),
            width = TEXTURE_WIDTH,
            nodeCount = bvh.nodeCount,
            primitiveStart = primitiveStart,
            materialStart = materialStart,
            look = look,
            sections = GpuSections(lightStart, emitters.size, memberStart, normalStart, records.last(), mediaStart),
            fogs = objects.filterIsInstance<Fog>().map { GpuFog(it.center, it.radius, media.indexOf(it.medium)) },
        )
    }

    /** Fixed-point code of [value]: [-RANGE, RANGE) maps to [0, 2^24). */
    fun encode(value: Double): Int =
        ((value + RANGE) / (2 * RANGE) * FIXED_ONE).roundToLong().coerceIn(0L, FIXED_ONE - 1L).toInt()

    fun decode(code: Int): Double = code.toDouble() / FIXED_ONE * (2 * RANGE) - RANGE

    private fun nodeCodes(bvh: FlatBvh, node: Int): IntArray {
        val box = IntArray(BOX_VALUES) { encode(bvh.boxes[node * BOX_VALUES + it]) }
        return box + intArrayOf(bvh.skip[node], bvh.first[node], bvh.count[node])
    }

    /** Kind, geometry, then [tail]: the normals record and the numbers of the media inside and outside. */
    private fun primitiveCodes(primitive: Traceable, tail: IntArray): IntArray {
        val (kind, geometry) = when (primitive) {
            is Sphere -> SPHERE to sphereGeometry(primitive)
            is Triangle -> TRIANGLE to triangleGeometry(primitive)
            is Fractal -> FRACTAL to fractalGeometry(primitive)
            else -> error("not a GPU primitive: $primitive")
        }
        val values = IntArray(PRIMITIVE_VALUES - 1 - tail.size) { geometry.getOrNull(it)?.let(::encode) ?: 0 }
        if (primitive is Fractal) {
            values[FRACTAL_KIND] = primitive.kind.ordinal
            values[FRACTAL_ITERATIONS] = primitive.iterations
        }
        return intArrayOf(kind) + values + tail
    }

    private fun mediumCodes(medium: Medium): IntArray {
        val values = listOf(medium.density, medium.albedo).flatMap { listOf(it.x, it.y, it.z) } + medium.g
        return IntArray(MEDIUM_VALUES) { encode(values[it]) }
    }

    private fun sphereGeometry(sphere: Sphere): DoubleArray = doubleArrayOf(
        sphere.center.x, sphere.center.y, sphere.center.z, sphere.radius,
        sphere.velocity.x, sphere.velocity.y, sphere.velocity.z,
    )

    /** Centre, radius, kind and iterations (both stored as whole numbers), parameter and seed of a fractal. */
    private fun fractalGeometry(fractal: Fractal): DoubleArray = doubleArrayOf(
        fractal.center.x, fractal.center.y, fractal.center.z, fractal.radius, 0.0, 0.0, fractal.parameter,
        fractal.seed.x, fractal.seed.y, fractal.seed.z,
    )

    private const val FRACTAL_KIND = 4
    private const val FRACTAL_ITERATIONS = 5

    /** First vertex and the edges from it to the other two, as the intersection test of the shader takes them. */
    private fun triangleGeometry(triangle: Triangle): DoubleArray {
        val edge1 = triangle.v1 - triangle.v0
        val edge2 = triangle.v2 - triangle.v0
        return doubleArrayOf(
            triangle.v0.x, triangle.v0.y, triangle.v0.z, edge1.x, edge1.y, edge1.z, edge2.x, edge2.y, edge2.z,
        )
    }

    private fun materialCodes(primitive: Traceable): IntArray {
        val values = GpuMaterials.values(materialOf(primitive))
        return IntArray(MATERIAL_VALUES) { if (it == 0) values[0].toInt() else encode(values[it]) }
    }

    private fun texture(codes: IntArray, height: Int): ByteArray {
        val bytes = ByteArray(TEXTURE_WIDTH * height * BYTES_PER_TEXEL)
        codes.forEachIndexed { index, code ->
            val at = index * BYTES_PER_TEXEL
            bytes[at + RED] = (code shr (2 * Byte.SIZE_BITS) and BYTE_MASK).toByte()
            bytes[at + GREEN] = (code shr Byte.SIZE_BITS and BYTE_MASK).toByte()
            bytes[at + BLUE] = (code and BYTE_MASK).toByte()
            bytes[at + ALPHA] = OPAQUE
        }
        return bytes
    }

}

/** The sphere or triangle the shader draws for [obj]: fog and translucent balls are spheres bounding media. */
private fun primitiveOf(obj: Traceable): Traceable? = when (obj) {
    is Sphere, is Triangle, is Fractal -> obj
    is Fog -> obj.boundary
    is Translucent -> obj.surface
    else -> null
}

/** Each primitive's slot in the leaves of [bvh], by identity: two equal triangles are two primitives. */
private fun slotsOf(bvh: FlatBvh, primitives: Array<Traceable>): Map<Traceable, Int> =
    java.util.IdentityHashMap<Traceable, Int>().apply {
        bvh.order.forEachIndexed { slot, index -> put(primitives[index], slot) }
    }

private fun materialOf(primitive: Traceable): Material = when (primitive) {
    is Sphere -> primitive.material
    is Triangle -> primitive.material
    is Fractal -> primitive.material
    else -> error("not a GPU primitive: $primitive")
}

private fun mediaOf(primitive: Traceable): MediumInterface? = when (primitive) {
    is Sphere -> primitive.media
    is Triangle -> primitive.media
    else -> null
}

private fun normalCodes(normals: VertexNormals): IntArray {
    val values = listOf(normals.n0, normals.n1, normals.n2).flatMap { listOf(it.x, it.y, it.z) }
    return IntArray(GpuScenePacking.NORMAL_VALUES) { GpuScenePacking.encode(values[it]) }
}

