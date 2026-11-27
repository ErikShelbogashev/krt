package krt.domain.trace

import kotlin.math.roundToLong
import krt.domain.scene.model.Checker
import krt.domain.scene.model.Conductor
import krt.domain.scene.model.Dielectric
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.Fog
import krt.domain.scene.model.Iridescent
import krt.domain.scene.model.Isotropic
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Material
import krt.domain.scene.model.Metal
import krt.domain.scene.model.Plastic
import krt.domain.scene.model.Sky
import krt.domain.scene.model.SoapBubble
import krt.domain.scene.model.Sphere
import krt.domain.scene.model.Traceable
import krt.domain.scene.model.Translucent
import krt.domain.scene.model.Triangle
import krt.domain.scene.model.Velvet
import krt.domain.scene.model.VertexNormals
import krt.platform.math.Point3

/**
 * Scene packed for the GPU shader (ADR 0009): one RGBA_8888 texture [width] texels wide, one number per texel.
 * A number is 24 bits in the R, G, B channels, alpha is always 255: a coordinate in fixed point
 * (see [GpuScenePacking.encode]) or a whole number (an index, a count, a code) as is.
 *
 * Five sections follow each other:
 * - [nodeCount] BVH nodes from texel 0, [GpuScenePacking.NODE_VALUES] each: box min x, y, z, max x, y, z,
 *   skip link, first primitive, primitive count (0 for an inner node); see [FlatBvh];
 * - [primitiveCount] primitives from [primitiveStart] in the order of the leaves, [GpuScenePacking.PRIMITIVE_VALUES]
 *   each: kind, then the centre and radius of a sphere, the first vertex and the two edges from it of a triangle
 *   or the centre, radius and density of a sphere of fog; last, for a triangle with vertex normals, 1 + the number
 *   of its record in the normals section, 0 for a flat one;
 * - their materials from [materialStart], [GpuScenePacking.MATERIAL_VALUES] each: material code, then a colour,
 *   two parameters, a second colour and a third parameter, as [GpuScenePacking.material] lays them out;
 * - [GpuLights.count] lights from [GpuLights.start]: the primitive slots of the emissive spheres and triangles,
 *   which next event estimation samples when [GpuLights.sampleLights] is on (ADR 0012);
 * - from [GpuLights.normalStart], the vertex normals of smooth triangles, [GpuScenePacking.NORMAL_VALUES] per record:
 *   the normals at the first, second and third vertex (ADR 0013).
 */
class GpuScene(
    val data: ByteArray,
    val width: Int,
    val nodeCount: Int,
    val primitiveStart: Int,
    val materialStart: Int,
    val sky: Sky = Sky.DAYLIGHT,
    val lights: GpuLights = GpuLights(materialStart, 0, sampleLights = false, normalStart = materialStart),
) {
    val primitiveCount: Int get() = (materialStart - primitiveStart) / GpuScenePacking.PRIMITIVE_VALUES

    /** Rows of the texture. */
    val height: Int get() = data.size / (width * GpuScenePacking.BYTES_PER_TEXEL)
}

/**
 * Where the light slots of a [GpuScene] lie, how many there are and whether the shader samples them; and where the
 * vertex normals that follow them start.
 */
data class GpuLights(val start: Int, val count: Int, val sampleLights: Boolean, val normalStart: Int) {
    /** The lights the shader samples: all of them, or none when light sampling is off. */
    val sampled: Int get() = if (sampleLights) count else 0
}

/** Packing of the scene model for the GPU shader: a pure transformation, mirrored by the shader's decoding. */
object GpuScenePacking {

    const val NODE_VALUES = 9
    const val PRIMITIVE_VALUES = 11
    const val NORMAL_VALUES = 9
    const val MATERIAL_VALUES = 10
    const val TEXTURE_WIDTH = 1024

    /** Packed coordinates lie in [-RANGE, RANGE). */
    const val RANGE = 4096.0

    const val SPHERE = 0
    const val TRIANGLE = 1
    const val MEDIUM = 2

    const val LAMBERT = 0
    const val METAL = 1
    const val DIELECTRIC = 2
    const val EMISSIVE = 3
    const val PLASTIC = 4
    const val CHECKER = 5
    const val ISOTROPIC = 6
    const val BUBBLE = 7
    const val IRIDESCENT = 8
    const val CONDUCTOR = 9
    const val VELVET = 10

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

    fun pack(objects: List<Traceable>, sky: Sky = Sky.DAYLIGHT, sampleLights: Boolean = false): GpuScene {
        val primitives = objects.flatMap { (it as? Translucent)?.parts ?: listOf(it) }
            .filter { it is Sphere || it is Triangle || it is Fog }.toTypedArray()
        val bvh = Bvh(primitives).flatten()
        val primitiveStart = bvh.nodeCount * NODE_VALUES
        val materialStart = primitiveStart + primitives.size * PRIMITIVE_VALUES
        val lightStart = materialStart + primitives.size * MATERIAL_VALUES
        val lightSlots = bvh.order.indices.filter { slot -> materialOf(primitives[bvh.order[slot]]) is Emissive }
        val normalStart = lightStart + lightSlots.size
        val smooth = bvh.order.map { (primitives[it] as? Triangle)?.normals }
        val records = smooth.runningFold(0) { count, normals -> if (normals == null) count else count + 1 }
        val codes = IntArray(normalStart + records.last() * NORMAL_VALUES)
        for (node in 0 until bvh.nodeCount) nodeCodes(bvh, node).copyInto(codes, node * NODE_VALUES)
        bvh.order.forEachIndexed { slot, index ->
            val record = smooth[slot]?.let { records[slot] + 1 } ?: 0
            primitiveCodes(primitives[index], record).copyInto(codes, primitiveStart + slot * PRIMITIVE_VALUES)
            materialCodes(primitives[index]).copyInto(codes, materialStart + slot * MATERIAL_VALUES)
            smooth[slot]?.let { normalCodes(it).copyInto(codes, normalStart + records[slot] * NORMAL_VALUES) }
        }
        lightSlots.toIntArray().copyInto(codes, lightStart)
        val height = maxOf(1, (codes.size + TEXTURE_WIDTH - 1) / TEXTURE_WIDTH)
        return GpuScene(
            data = texture(codes, height),
            width = TEXTURE_WIDTH,
            nodeCount = bvh.nodeCount,
            primitiveStart = primitiveStart,
            materialStart = materialStart,
            sky = sky,
            lights = GpuLights(lightStart, lightSlots.size, sampleLights && lightSlots.isNotEmpty(), normalStart),
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

    private fun primitiveCodes(primitive: Traceable, normalRecord: Int): IntArray {
        val (kind, geometry) = when (primitive) {
            is Sphere -> SPHERE to sphereGeometry(primitive)
            is Triangle -> TRIANGLE to triangleGeometry(primitive)
            is Fog -> MEDIUM to doubleArrayOf(
                primitive.center.x, primitive.center.y, primitive.center.z, primitive.radius, primitive.density,
            )
            else -> error("not a GPU primitive: $primitive")
        }
        val values = IntArray(PRIMITIVE_VALUES - 2) { geometry.getOrNull(it)?.let(::encode) ?: 0 }
        return intArrayOf(kind) + values + normalRecord
    }

    private fun sphereGeometry(sphere: Sphere): DoubleArray =
        doubleArrayOf(sphere.center.x, sphere.center.y, sphere.center.z, sphere.radius)

    /** First vertex and the edges from it to the other two, as the intersection test of the shader takes them. */
    private fun triangleGeometry(triangle: Triangle): DoubleArray {
        val edge1 = triangle.v1 - triangle.v0
        val edge2 = triangle.v2 - triangle.v0
        return doubleArrayOf(
            triangle.v0.x, triangle.v0.y, triangle.v0.z, edge1.x, edge1.y, edge1.z, edge2.x, edge2.y, edge2.z,
        )
    }

    private fun materialCodes(primitive: Traceable): IntArray {
        val values = material(materialOf(primitive))
        return IntArray(MATERIAL_VALUES) { if (it == 0) values[0].toInt() else encode(values[it]) }
    }

    /**
     * The values of [material]: code; colour r, g, b; parameters 1 and 2; second colour r, g, b; parameter 3.
     * - Lambert: albedo;
     * - Metal: albedo, fuzz;
     * - Dielectric: absorption per unit of distance (Beer–Lambert), refraction index, dispersion, none, GGX α of
     *   frosted glass (0 for clear glass);
     * - Emissive: emitted light;
     * - Plastic: albedo, coat roughness, coat refraction index;
     * - Checker: even cells, coat roughness, coat refraction index, odd cells, cell size;
     * - Isotropic (the particles of fog): albedo;
     * - Soap bubble: none, film thickness in nm, film refraction index;
     * - Iridescent: base, film thickness in nm, film refraction index, none, roughness;
     * - Conductor: η, GGX α along and across the tangent, k;
     * - Velvet: base, roughness, chance of the diffuse bounce, sheen.
     */
    fun material(material: Material): DoubleArray = when (material) {
        is Lambert -> values(LAMBERT, material.albedo)
        is Metal -> values(METAL, material.albedo, material.fuzz)
        is Dielectric -> values(
            DIELECTRIC, material.absorption, material.indexOfReflection, material.dispersion,
            third = if (material.roughness > 0) material.facets.alphaX else 0.0,
        )
        is Conductor -> values(
            CONDUCTOR, material.eta, material.facets.alphaX, material.facets.alphaY, odd = material.k,
        )
        is Emissive -> values(EMISSIVE, material.emitColor * material.intensity)
        is Plastic -> values(PLASTIC, material.albedo, material.roughness, material.ior)
        is Checker -> values(
            CHECKER, material.even, material.roughness, material.ior, odd = material.odd, third = material.cellSize,
        )
        is Isotropic -> values(ISOTROPIC, material.albedo)
        is Velvet -> values(VELVET, material.base, material.roughness, material.diffuseChance, odd = material.sheen)
        is SoapBubble -> values(BUBBLE, Point3.ZERO, material.thickness, material.filmIor)
        is Iridescent -> values(
            IRIDESCENT, material.base, material.thickness, material.filmIor, third = material.roughness,
        )
        else -> values(LAMBERT, Point3(UNKNOWN_GREY, UNKNOWN_GREY, UNKNOWN_GREY))
    }

    @Suppress("LongParameterList")
    private fun values(
        code: Int,
        color: Point3,
        first: Double = 0.0,
        second: Double = 0.0,
        odd: Point3 = Point3.ZERO,
        third: Double = 0.0,
    ): DoubleArray =
        doubleArrayOf(code.toDouble(), color.x, color.y, color.z, first, second, odd.x, odd.y, odd.z, third)

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

    /** Colour of a material the shader does not know. */
    private const val UNKNOWN_GREY = 0.5
}

private fun materialOf(primitive: Traceable): Material = when (primitive) {
    is Sphere -> primitive.material
    is Triangle -> primitive.material
    is Fog -> primitive.material
    else -> error("not a GPU primitive: $primitive")
}

private fun normalCodes(normals: VertexNormals): IntArray {
    val values = listOf(normals.n0, normals.n1, normals.n2).flatMap { listOf(it.x, it.y, it.z) }
    return IntArray(GpuScenePacking.NORMAL_VALUES) { GpuScenePacking.encode(values[it]) }
}

