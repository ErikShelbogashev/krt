package krt.domain.scene.model

import kotlin.math.exp
import kotlin.math.expm1
import kotlin.math.pow
import kotlin.random.Random
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

/** Side of the geometric surface which emits light. A reversed spherical PBRT emitter radiates inward. */
enum class EmissionSide(val sign: Double) {
    BOTH(0.0), FRONT(1.0), BACK(-1.0);

    fun visible(front: Boolean): Boolean = this == BOTH || (this == FRONT) == front
}

class Emissive(
    val emitColor: Color,
    val intensity: Double,
    override val emissionSide: EmissionSide = EmissionSide.BOTH,
) : Material {

    /** Reverse a sphere emitter's normal without changing its geometry. Mesh orientation lives in its winding. */
    fun reversed(): Emissive = when (emissionSide) {
        EmissionSide.FRONT -> Emissive(emitColor, intensity, EmissionSide.BACK)
        EmissionSide.BACK -> Emissive(emitColor, intensity, EmissionSide.FRONT)
        EmissionSide.BOTH -> this
    }

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
     * to a luminance of 1, as pbrt-v4 scales its black bodies (ADR 0016).
     */
    fun color(kelvin: Double): Color {
        if (!(kelvin > 0.0) || !kelvin.isFinite()) return Color.ZERO
        val step = (Spectrum.MAX_NM - Spectrum.MIN_NM) / STEPS
        val last = Spectrum.MAX_NM - 0.5 * step
        val sum = (0 until STEPS).fold(Point3.ZERO) { acc, i ->
            val wavelength = Spectrum.MIN_NM + (i + 0.5) * step
            val light = if (kelvin in MIN_DIRECT_K..MAX_DIRECT_K) radiance(wavelength, kelvin)
                else relativeRadiance(wavelength, last, kelvin)
            acc + Spectrum.weight(wavelength) * light
        }
        return sum / sum.dot(LUMINANCE)
    }

    /** Weights of the red, green and blue channels in luminance (Rec. 709). */
    private val LUMINANCE = Point3(0.2126, 0.7152, 0.0722)

    // Ordinary scene temperatures retain the established quadrature and its rounding in serialized scenes.
    private const val MIN_DIRECT_K = 100.0
    private const val MAX_DIRECT_K = 1e6
    private const val PLANCK_WAVELENGTH_POWER = 5
    private const val HC_OVER_K_NM = PLANCK * LIGHT_SPEED / (BOLTZMANN * METRES_PER_NM)

    /** B(λ,T) / B(last,T). Factor the exponent difference before division by T to avoid ∞ − ∞. */
    private fun relativeRadiance(wavelength: Double, last: Double, kelvin: Double): Double {
        val x = (HC_OVER_K_NM / wavelength) / kelvin
        val xLast = (HC_OVER_K_NM / last) / kelvin
        val difference = (HC_OVER_K_NM * ((last - wavelength) / (last * wavelength))) / kelvin
        return (last / wavelength).pow(PLANCK_WAVELENGTH_POWER) * exp(-difference) * (expm1(-xLast) / expm1(-x))
    }

    /** Planck's law, up to a constant factor. */
    private fun radiance(wavelength: Double, kelvin: Double): Double {
        val metres = wavelength * METRES_PER_NM
        val metresCubed = metres * metres * metres
        return 1 / (metresCubed * metres * metres * (exp(PLANCK * LIGHT_SPEED / (metres * BOLTZMANN * kelvin)) - 1))
    }
}
