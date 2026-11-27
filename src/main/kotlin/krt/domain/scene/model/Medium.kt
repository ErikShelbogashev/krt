package krt.domain.scene.model

import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

/**
 * A homogeneous participating medium (ADR 0014): light travelling a unit of distance through it scatters or is
 * absorbed with the chance [density] per colour channel (the extinction σt), and of what it meets the share
 * [albedo] scatters, equally in every direction; the rest is absorbed. Fog, smoke and the inside of wax, jade or
 * skin are media.
 */
data class Medium(val density: Color, val albedo: Color) {

    /** Share of light left after [distance] through the medium, per channel. */
    fun transmittance(distance: Double): Color =
        Color(exp(-density.x * distance), exp(-density.y * distance), exp(-density.z * distance))

    companion object {
        /** A grey medium of one [density] for every channel. */
        fun grey(density: Double, albedo: Color): Medium = Medium(Color(density, density, density), albedo)

        /** The medium in which light travels [meanFreePath] per channel on average before it meets a particle. */
        fun ofMeanFreePath(meanFreePath: Color, albedo: Color): Medium =
            Medium(Color(1 / meanFreePath.x, 1 / meanFreePath.y, 1 / meanFreePath.z), albedo)
    }
}

/** The media on the two sides of a closed surface: [inside] it and [outside] it; null for empty space. */
data class MediumInterface(val inside: Medium?, val outside: Medium? = null)

/**
 * A surface that only bounds a medium, like the edge of a bank of fog: light passes it straight and unchanged,
 * and only the medium it travels in changes.
 */
object Interface : Material {
    override fun scatter(ray: Ray, hit: Hit, random: Random): Scattered =
        Scattered(ray.next(hit.p, ray.direction), Point3.ONE, Lobe.SPECULAR)
}

/**
 * Free flights through a medium (ADR 0014), the same on the CPU and in the shader. A distance is sampled with the
 * density of one channel picked at random, and weighed by the mean over the channels (spectral MIS), so media of
 * coloured density stay unbiased.
 */
object FreeFlight {
    private const val CHANNELS = 3

    /** The distance to the next particle for uniform numbers [channel] and [u]; infinite in a clear channel. */
    fun distance(medium: Medium, channel: Double, u: Double): Double {
        val density = when ((channel * CHANNELS).toInt().coerceAtMost(CHANNELS - 1)) {
            0 -> medium.density.x
            1 -> medium.density.y
            else -> medium.density.z
        }
        return if (density > 0) -ln(1 - u) / density else Double.POSITIVE_INFINITY
    }

    /** Weight of a scattering at [distance]: scattering density × transmittance / pdf of the distance. */
    fun scatterWeight(medium: Medium, distance: Double): Color {
        val transmittance = medium.transmittance(distance)
        val pdf = (medium.density * transmittance).let { (it.x + it.y + it.z) / CHANNELS }
        return medium.albedo * medium.density * transmittance / pdf
    }

    /** Weight of passing [distance] through the medium untouched: transmittance / the chance of it. */
    fun passWeight(medium: Medium, distance: Double): Color {
        val transmittance = medium.transmittance(distance)
        return transmittance / ((transmittance.x + transmittance.y + transmittance.z) / CHANNELS)
    }
}
