package krt.domain.scene.model

import kotlin.math.exp
import kotlin.random.Random
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

class Emissive(val emitColor: Color, val intensity: Double) : Material {

    override fun scatter(ray: Ray, hit: Hit, random: Random): Scattered? {
        // Emissive material does not scatter light, it emits light
        return null
    }

    override fun emitted(u: Double, v: Double, p: Point3): Color {
        // Return the color that the material emits, scaled by the intensity
        return emitColor * intensity
    }

    override val emits: Boolean get() = true

    companion object {
        /** A light glowing like a black body at [kelvin]: a candle at 1900 K, daylight at 6500 K, sky at 10000 K. */
        fun kelvin(kelvin: Double, intensity: Double): Emissive = Emissive(Blackbody.color(kelvin), intensity)
    }
}

/**
 * A surface glowing in a [pattern], like lava, a neon sign or a screen: the pattern's colour at the point times
 * [intensity] (ADR 0013). Light sampling treats it as a light like any other.
 */
class GlowingPattern(val pattern: Pattern, val intensity: Double) : Material {
    override fun scatter(ray: Ray, hit: Hit, random: Random): Scattered? = null

    override fun emitted(u: Double, v: Double, p: Point3): Color = pattern.colorAt(p) * intensity

    override val emits: Boolean get() = true
}

/** Colour of black-body light (ADR 0013). */
object Blackbody {
    private const val PLANCK = 6.62607015e-34
    private const val LIGHT_SPEED = 2.99792458e8
    private const val BOLTZMANN = 1.380649e-23
    private const val METRES_PER_NM = 1e-9
    private const val STEPS = 68

    /**
     * Planck's spectrum at [kelvin] seen through the channel weights of [Spectrum]: the colour of the glow, scaled
     * so its brightest channel is 1.
     */
    fun color(kelvin: Double): Color {
        val step = (Spectrum.MAX_NM - Spectrum.MIN_NM) / STEPS
        val sum = (0 until STEPS).fold(Point3.ZERO) { acc, i ->
            val wavelength = Spectrum.MIN_NM + (i + 0.5) * step
            acc + Spectrum.weight(wavelength) * radiance(wavelength, kelvin)
        }
        return sum / maxOf(sum.x, sum.y, sum.z)
    }

    /** Planck's law, up to a constant factor. */
    private fun radiance(wavelength: Double, kelvin: Double): Double {
        val metres = wavelength * METRES_PER_NM
        val metresCubed = metres * metres * metres
        return 1 / (metresCubed * metres * metres * (exp(PLANCK * LIGHT_SPEED / (metres * BOLTZMANN * kelvin)) - 1))
    }
}
