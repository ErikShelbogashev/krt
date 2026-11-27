package krt.domain.trace

import kotlin.math.roundToLong
import krt.domain.scene.model.Dielectric
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Material
import krt.domain.scene.model.Metal
import krt.domain.scene.model.Sphere
import krt.domain.scene.model.Traceable

/**
 * Scene packed for the GPU shader (ADR 0009): one RGBA_8888 texture [width] texels wide, one number per texel.
 * A number is 24 bits in the R, G, B channels, alpha is always 255: a coordinate in fixed point
 * (see [GpuScenePacking.encode]) or a whole number (an index, a count, a code) as is.
 *
 * Three sections follow each other:
 * - [nodeCount] BVH nodes from texel 0, [GpuScenePacking.NODE_VALUES] each: box min x, y, z, max x, y, z,
 *   skip link, first primitive, primitive count (0 for an inner node); see [FlatBvh];
 * - [primitiveCount] primitives from [primitiveStart] in the order of the leaves, [GpuScenePacking.PRIMITIVE_VALUES]
 *   each: kind, then the centre and radius of a sphere;
 * - their materials from [materialStart], [GpuScenePacking.MATERIAL_VALUES] each: material code, colour r, g, b
 *   and one parameter (fuzz of a metal, refraction index of a dielectric).
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
    const val MATERIAL_VALUES = 5
    const val TEXTURE_WIDTH = 1024

    /** Packed coordinates lie in [-RANGE, RANGE). */
    const val RANGE = 4096.0

    const val SPHERE = 0

    const val LAMBERT = 0
    const val METAL = 1
    const val DIELECTRIC = 2
    const val EMISSIVE = 3

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
        val primitives = objects.filterIsInstance<Sphere>().toTypedArray<Traceable>()
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
        val sphere = primitive as Sphere
        val geometry = doubleArrayOf(sphere.center.x, sphere.center.y, sphere.center.z, sphere.radius)
        return intArrayOf(SPHERE) + IntArray(PRIMITIVE_VALUES - 1) { geometry.getOrNull(it)?.let(::encode) ?: 0 }
    }

    private fun materialCodes(primitive: Traceable): IntArray {
        val values = material((primitive as Sphere).material)
        return IntArray(MATERIAL_VALUES) { if (it == 0) values[0].toInt() else encode(values[it]) }
    }

    private fun material(material: Material): DoubleArray = when (material) {
        is Lambert -> doubleArrayOf(LAMBERT.toDouble(), material.albedo.x, material.albedo.y, material.albedo.z, 0.0)
        is Metal -> doubleArrayOf(
            METAL.toDouble(), material.albedo.x, material.albedo.y, material.albedo.z, material.fuzz,
        )
        is Dielectric -> doubleArrayOf(DIELECTRIC.toDouble(), 1.0, 1.0, 1.0, material.indexOfReflection)
        is Emissive -> {
            val light = material.emitColor * material.intensity
            doubleArrayOf(EMISSIVE.toDouble(), light.x, light.y, light.z, 0.0)
        }
        else -> doubleArrayOf(LAMBERT.toDouble(), UNKNOWN_GREY, UNKNOWN_GREY, UNKNOWN_GREY, 0.0)
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

    /** Colour of a material the shader does not know. */
    private const val UNKNOWN_GREY = 0.5
}
