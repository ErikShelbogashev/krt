package krt.domain.scene.model

import kotlin.math.floor
import kotlin.math.sqrt
import krt.platform.math.PixelDifferentials
import krt.platform.math.Point3

/** Width of the ring phase and fraction of fine Perlin fibre contrast supported by this pixel. */
internal data class WoodFilter(val ringWidth: Double, val fibres: Double)

internal object WoodGrain {
    private val waveScale = Point3(0.25, 2.0, 2.0)
    private val fibreScale = Point3(0.5, 24.0, 24.0)
    private const val START = 0.72
    private const val PEAK = 0.9
    private const val MEAN = (1 - START) / 2
    private const val SHARE = 0.75
    private const val MIN_WIDTH = 0.001
    private const val MAX_WIDTH = 32.0
    private const val FIBRES_SHARP = 0.25
    private const val FIBRES_UNRESOLVED = 2.0
    private const val CUBIC_COEFFICIENT = 3.0

    fun filter(pattern: Pattern, point: Point3, pixel: PixelDifferentials): WoodFilter {
        val q = point * pattern.scale
        val dx = pixel.dx * pattern.scale
        val dy = pixel.dy * pattern.scale
        val radius = Math.hypot(q.y, q.z)
        fun phaseVariance(d: Point3): Double {
            val radial = if (radius > 0.0) q.y / radius * d.y + q.z / radius * d.z else Math.hypot(d.y, d.z)
            val ring = pattern.frequency * radial
            // The warp has no preferred slope. Its phase variation saturates across independent noise cells.
            val warp = pattern.turbulence * minOf(1.0, (d * waveScale).length())
            return ring * ring + warp * warp
        }
        // A box with this width has the same phase variance as the two projected film axes.
        val rings = sqrt(phaseVariance(dx) + phaseVariance(dy))
        val span = maxOf((dx * fibreScale).length(), (dy * fibreScale).length())
        return WoodFilter(rings, 1 - smooth((span - FIBRES_SHARP) / (FIBRES_UNRESOLVED - FIBRES_SHARP)))
    }

    fun value(q: Point3, frequency: Double, turbulence: Double, filter: WoodFilter?): Double {
        val rings = sqrt(q.y * q.y + q.z * q.z) * frequency + turbulence * Noise.perlin(q * waveScale)
        val phase = rings - floor(rings)
        val width = filter?.ringWidth ?: 0.0
        val latewood = when {
            width <= MIN_WIDTH -> smooth((phase - START) / (PEAK - START)) * (1 - smooth((phase - PEAK) / (1 - PEAK)))
            width >= MAX_WIDTH -> MEAN
            else -> (integral(phase + width / 2) - integral(phase - width / 2)) / width
        }
        val amplitude = filter?.fibres ?: 1.0
        val fibres = if (amplitude == 0.0) 0.5 else 0.5 + 0.5 * amplitude * Noise.perlin(q * fibreScale)
        return SHARE * latewood + (1 - SHARE) * fibres
    }

    /** Periodic antiderivative of the two cubic smoothsteps describing one latewood ring. */
    private fun integral(x: Double): Double {
        val whole = floor(x)
        val phase = x - whole
        val up = ((phase - START) / (PEAK - START)).coerceIn(0.0, 1.0)
        val down = ((phase - PEAK) / (1 - PEAK)).coerceIn(0.0, 1.0)
        return whole * MEAN + (PEAK - START) * primitive(up) + (1 - PEAK) * (down - primitive(down))
    }

    private fun primitive(t: Double) = t * t * t * (1 - t / 2)

    private fun smooth(x: Double): Double {
        val t = x.coerceIn(0.0, 1.0)
        return t * t * (CUBIC_COEFFICIENT - 2 * t)
    }
}
