package krt.domain.scene.model

import kotlin.math.abs
import kotlin.math.floor
import krt.platform.math.Point3

/**
 * Perlin's improved gradient noise and fractal sums of it (ADR 0013). The lattice hash is the permutation polynomial
 * (34x² + x) mod 289, which stays exact in 32-bit floats, so the GPU shader computes the very same noise.
 */
object Noise {
    private const val PERIOD = 289.0
    private const val PERMUTE_MUL = 34.0
    private const val GRADIENTS = 16.0
    private const val FIRST_HALF = 8.0
    private const val FIRST_QUARTER = 4.0
    private const val X_LIKE_A = 12.0
    private const val X_LIKE_B = 14.0
    private const val FADE_A = 6.0
    private const val FADE_B = 15.0
    private const val FADE_C = 10.0

    /** Octaves of the fractal sums. */
    const val OCTAVES = 5

    /** Octaves of a fractal sum at most, as many as the shader's loop runs. */
    const val MAX_OCTAVES = 8
    private const val LACUNARITY = 2.0
    private const val GAIN = 0.5

    /** Noise at [p], in about [-1, 1], 0 at the lattice points. */
    fun perlin(p: Point3): Double {
        val cell = Point3(floor(p.x), floor(p.y), floor(p.z))
        val f = p - cell
        val i = Point3(cell.x.mod(PERIOD), cell.y.mod(PERIOD), cell.z.mod(PERIOD))
        val u = Point3(fade(f.x), fade(f.y), fade(f.z))
        fun corner(dx: Double, dy: Double, dz: Double): Double =
            gradient(permute(permute(permute(i.x + dx) + i.y + dy) + i.z + dz), f.x - dx, f.y - dy, f.z - dz)
        val x00 = lerp(corner(0.0, 0.0, 0.0), corner(1.0, 0.0, 0.0), u.x)
        val x10 = lerp(corner(0.0, 1.0, 0.0), corner(1.0, 1.0, 0.0), u.x)
        val x01 = lerp(corner(0.0, 0.0, 1.0), corner(1.0, 0.0, 1.0), u.x)
        val x11 = lerp(corner(0.0, 1.0, 1.0), corner(1.0, 1.0, 1.0), u.x)
        return lerp(lerp(x00, x10, u.y), lerp(x01, x11, u.y), u.z)
    }

    /** Fractal Brownian motion: [octaves] octaves of noise, each twice as fine and half as strong. */
    fun fbm(p: Point3, octaves: Int = OCTAVES): Double {
        var sum = 0.0
        var amplitude = 1.0
        var point = p
        repeat(octaves.coerceIn(1, MAX_OCTAVES)) {
            sum += amplitude * perlin(point)
            amplitude *= GAIN
            point = point * LACUNARITY
        }
        return sum
    }

    /** Turbulence: the fractal sum of the noise's absolute value, creased where the noise crosses 0. */
    fun turbulence(p: Point3, octaves: Int = OCTAVES): Double {
        var sum = 0.0
        var amplitude = 1.0
        var point = p
        repeat(octaves.coerceIn(1, MAX_OCTAVES)) {
            sum += amplitude * abs(perlin(point))
            amplitude *= GAIN
            point = point * LACUNARITY
        }
        return sum
    }

    private fun permute(x: Double): Double = ((PERMUTE_MUL * x + 1) * x).mod(PERIOD)

    private fun fade(t: Double): Double = t * t * t * (t * (t * FADE_A - FADE_B) + FADE_C)

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    /** Dot product with one of Perlin's twelve edge gradients, picked by the low four bits of [hash]. */
    private fun gradient(hash: Double, x: Double, y: Double, z: Double): Double {
        val h = hash.mod(GRADIENTS)
        val u = if (h < FIRST_HALF) x else y
        val v = if (h < FIRST_QUARTER) y else if (h == X_LIKE_A || h == X_LIKE_B) x else z
        val first = if (h.mod(2.0) == 0.0) u else -u
        val second = if (floor(h / 2).mod(2.0) == 0.0) v else -v
        return first + second
    }
}
