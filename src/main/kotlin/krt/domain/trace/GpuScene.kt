package krt.domain.trace

import kotlin.math.roundToLong
import krt.domain.scene.model.Checker
import krt.domain.scene.model.Dielectric
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Material
import krt.domain.scene.model.Metal
import krt.domain.scene.model.Plastic
import krt.domain.scene.model.Sphere
import krt.domain.scene.model.Traceable
import krt.domain.scene.model.Triangle
import krt.platform.math.Point3

/**
 * Scene packed for the GPU shader (ADR 0009): one RGBA_8888 texture [width] texels wide, one number per texel.
 * A number is 24 bits in the R, G, B channels, alpha is always 255: a coordinate in fixed point
 * (see [GpuScenePacking.encode]) or a whole number (an index, a count, a code) as is.
 *
 * Three sections follow each other:
 * - [nodeCount] BVH nodes from texel 0, [GpuScenePacking.NODE_VALUES] each: box min x, y, z, max x, y, z,
 *   skip link, first primitive, primitive count (0 for an inner node); see [FlatBvh];
 * - [primitiveCount] primitives from [primitiveStart] in the order of the leaves, [GpuScenePacking.PRIMITIVE_VALUES]
 *   each: kind, then the centre and radius of a sphere or the first vertex and the two edges from it of a triangle;
 * - their materials from [materialStart], [GpuScenePacking.MATERIAL_VALUES] each: material code, then a colour,
 *   two parameters, a second colour and a third parameter, as [GpuScenePacking.material] lays them out.
 */
class GpuScene(
    val data: ByteArray,
    val width: Int,
    val height: Int,
    val nodeCount: Int,
    val primitiveCount: Int,
    val primitiveStart: Int,
    val materialStart: Int,
)

/** Packing of the scene model for the GPU shader: a pure transformation, mirrored by the shader's decoding. */
object GpuScenePacking {

    const val NODE_VALUES = 9
    const val PRIMITIVE_VALUES = 10
    const val MATERIAL_VALUES = 10
    const val TEXTURE_WIDTH = 1024

    /** Packed coordinates lie in [-RANGE, RANGE). */
    const val RANGE = 4096.0

    const val SPHERE = 0
    const val TRIANGLE = 1

    const val LAMBERT = 0
    const val METAL = 1
    const val DIELECTRIC = 2
    const val EMISSIVE = 3
    const val PLASTIC = 4
    const val CHECKER = 5

    private const val FIXED_BITS = 24
    private const val FIXED_ONE = 1 shl FIXED_BITS
    private const val BYTES_PER_TEXEL = 4
    private const val BYTE_MASK = 0xFF
    private const val OPAQUE: Byte = -1
    private const val RED = 0
    private const val GREEN = 1
    private const val BLUE = 2
    private const val ALPHA = 3
    private const val BOX_VALUES = 6

    fun pack(objects: List<Traceable>): GpuScene {
        val primitives = objects.filter { it is Sphere || it is Triangle }.toTypedArray()
        val bvh = Bvh(primitives).flatten()
        val primitiveStart = bvh.nodeCount * NODE_VALUES
        val materialStart = primitiveStart + primitives.size * PRIMITIVE_VALUES
        val codes = IntArray(materialStart + primitives.size * MATERIAL_VALUES)
        for (node in 0 until bvh.nodeCount) nodeCodes(bvh, node).copyInto(codes, node * NODE_VALUES)
        bvh.order.forEachIndexed { slot, index ->
            primitiveCodes(primitives[index]).copyInto(codes, primitiveStart + slot * PRIMITIVE_VALUES)
            materialCodes(primitives[index]).copyInto(codes, materialStart + slot * MATERIAL_VALUES)
        }
        val height = maxOf(1, (codes.size + TEXTURE_WIDTH - 1) / TEXTURE_WIDTH)
        return GpuScene(
            data = texture(codes, height),
            width = TEXTURE_WIDTH,
            height = height,
            nodeCount = bvh.nodeCount,
            primitiveCount = primitives.size,
            primitiveStart = primitiveStart,
            materialStart = materialStart,
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

    private fun primitiveCodes(primitive: Traceable): IntArray {
        val (kind, geometry) = when (primitive) {
            is Sphere -> SPHERE to sphereGeometry(primitive)
            is Triangle -> TRIANGLE to triangleGeometry(primitive)
            else -> error("not a GPU primitive: $primitive")
        }
        return intArrayOf(kind) + IntArray(PRIMITIVE_VALUES - 1) { geometry.getOrNull(it)?.let(::encode) ?: 0 }
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
        val values = material(
            when (primitive) {
                is Sphere -> primitive.material
                is Triangle -> primitive.material
                else -> error("not a GPU primitive: $primitive")
            },
        )
        return IntArray(MATERIAL_VALUES) { if (it == 0) values[0].toInt() else encode(values[it]) }
    }

    /**
     * The values of [material]: code; colour r, g, b; parameters 1 and 2; second colour r, g, b; parameter 3.
     * - Lambert: albedo;
     * - Metal: albedo, fuzz;
     * - Dielectric: absorption per unit of distance (Beer–Lambert), refraction index;
     * - Emissive: emitted light;
     * - Plastic: albedo, coat roughness, coat refraction index;
     * - Checker: even cells, coat roughness, coat refraction index, odd cells, cell size.
     */
    fun material(material: Material): DoubleArray = when (material) {
        is Lambert -> values(LAMBERT, material.albedo)
        is Metal -> values(METAL, material.albedo, material.fuzz)
        is Dielectric -> values(DIELECTRIC, material.absorption, material.indexOfReflection)
        is Emissive -> values(EMISSIVE, material.emitColor * material.intensity)
        is Plastic -> values(PLASTIC, material.albedo, material.roughness, material.ior)
        is Checker -> values(
            CHECKER, material.even, material.roughness, material.ior, odd = material.odd, third = material.cellSize,
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
