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
 * Scene packed for the GPU shader (ADR 0009).
 *
 * [spheres] holds four floats per sphere (centre x, y, z, radius) for a uniform array of the shader.
 * [materials] is an RGBA_8888 texture [materialsWidth] texels wide, [MATERIAL_VALUES] texels per sphere:
 * material code, colour r, g, b and one parameter (fuzz of a metal, refraction index of a dielectric).
 * Every texel is one number in 24-bit fixed point (R, G, B channels), alpha is always 255.
 */
class GpuScene(
    val spheres: FloatArray,
    val sphereCount: Int,
    val materials: ByteArray,
    val materialsWidth: Int,
    val materialsHeight: Int,
)

/** Packing of the scene model for the GPU shader: a pure transformation, mirrored by the shader's decoding. */
object GpuScenePacking {

    /** Size of the shader's sphere array; spheres beyond it are not drawn in GPU mode. */
    const val MAX_SPHERES = 512
    const val MATERIAL_VALUES = 5
    const val TEXTURE_WIDTH = 1024

    /** Packed numbers lie in [-RANGE, RANGE). */
    const val RANGE = 4096.0

    const val LAMBERT = 0
    const val METAL = 1
    const val DIELECTRIC = 2
    const val EMISSIVE = 3

    private const val FIXED_BITS = 24
    private const val FIXED_ONE = 1 shl FIXED_BITS
    private const val BYTES_PER_TEXEL = 4
    private const val BYTE_MASK = 0xFF
    private const val OPAQUE: Byte = -1
    private const val SPHERE_FLOATS = 4
    private const val RED = 0
    private const val GREEN = 1
    private const val BLUE = 2
    private const val ALPHA = 3

    fun pack(objects: List<Traceable>): GpuScene {
        val spheres = objects.filterIsInstance<Sphere>().take(MAX_SPHERES)
        val geometry = FloatArray(MAX_SPHERES * SPHERE_FLOATS)
        val values = DoubleArray(spheres.size * MATERIAL_VALUES)
        spheres.forEachIndexed { index, sphere ->
            floats(sphere.center.x, sphere.center.y, sphere.center.z, sphere.radius)
                .copyInto(geometry, index * SPHERE_FLOATS)
            material(sphere.material).copyInto(values, index * MATERIAL_VALUES)
        }
        val height = maxOf(1, (values.size + TEXTURE_WIDTH - 1) / TEXTURE_WIDTH)
        return GpuScene(geometry, spheres.size, texture(values, height), TEXTURE_WIDTH, height)
    }

    /** Fixed-point code of [value]: [-RANGE, RANGE) maps to [0, 2^24). */
    fun encode(value: Double): Int =
        ((value + RANGE) / (2 * RANGE) * FIXED_ONE).roundToLong().coerceIn(0L, FIXED_ONE - 1L).toInt()

    fun decode(code: Int): Double = code.toDouble() / FIXED_ONE * (2 * RANGE) - RANGE

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

    private fun texture(values: DoubleArray, height: Int): ByteArray {
        val bytes = ByteArray(TEXTURE_WIDTH * height * BYTES_PER_TEXEL)
        values.forEachIndexed { index, value ->
            val code = encode(value)
            val at = index * BYTES_PER_TEXEL
            bytes[at + RED] = (code shr (2 * Byte.SIZE_BITS) and BYTE_MASK).toByte()
            bytes[at + GREEN] = (code shr Byte.SIZE_BITS and BYTE_MASK).toByte()
            bytes[at + BLUE] = (code and BYTE_MASK).toByte()
            bytes[at + ALPHA] = OPAQUE
        }
        return bytes
    }

    private fun floats(vararg values: Double) = FloatArray(values.size) { values[it].toFloat() }

    /** Colour of a material the shader does not know. */
    private const val UNKNOWN_GREY = 0.5
}
