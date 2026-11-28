package krt.domain.scene.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

/**
 * A homogeneous participating medium (ADR 0014): light travelling a unit of distance through it scatters or is
 * absorbed with the chance [density] per colour channel (the extinction σt), and of what it meets the share
 * [albedo] scatters; the rest is absorbed. Fog, smoke and the inside of wax, jade or skin are media. A particle
 * scatters light by the phase function of Henyey and Greenstein with the mean cosine [g]: 0 scatters equally in
 * every direction, towards 1 more and more onwards, as haze does, which makes the shafts of light in it.
 */
data class Medium(val density: Color, val albedo: Color, val g: Double = 0.0) {

    /** Share of light left after [distance] through the medium, per channel. */
    fun transmittance(distance: Double): Color =
        Color(beer(density.x, distance), beer(density.y, distance), beer(density.z, distance))

    private fun beer(extinction: Double, distance: Double): Double =
        if (extinction == 0.0) 1.0 else exp(-extinction * distance)

    companion object {
        /** A grey medium of one [density] for every channel. */
        fun grey(density: Double, albedo: Color): Medium = Medium(Color(density, density, density), albedo)

        /** The medium in which light travels [meanFreePath] per channel on average before it meets a particle. */
        fun ofMeanFreePath(meanFreePath: Color, albedo: Color): Medium =
            Medium(Color(1 / meanFreePath.x, 1 / meanFreePath.y, 1 / meanFreePath.z), albedo)
    }
}

/** The phase function of Henyey and Greenstein (ADR 0014): how a particle shares out the light it scatters. */
object HenyeyGreenstein {
    private const val FOUR_PI = 4 * PI
    private const val ISOTROPIC = 1e-3

    /** Share of the light per steradian scattered at [cosine] from the direction it came, for the mean cosine [g]. */
    fun density(cosine: Double, g: Double): Double {
        val magnitude = abs(g)
        val angle = cosine.coerceIn(-1.0, 1.0)
        val separation = if (g < 0.0) 1 + angle else 1 - angle
        val gap = 1 - magnitude
        val d = gap * gap + 2 * magnitude * separation
        return gap * (1 + magnitude) / (FOUR_PI * d * sqrt(d))
    }

    /** A direction scattered from the unit [incoming] direction, drawn in proportion to [density] from [u] and [v]. */
    fun sample(incoming: Point3, g: Double, u: Double, v: Double): Point3 {
        val cosine = if (abs(g) < ISOTROPIC) {
            // Expand the inverse CDF before division by g. This keeps the first moment even near isotropy.
            val t = 2 * u - 1
            val d = 1 + g * t
            (t * (1 + g * g) + 0.5 * g * (3 + t * t + g * g * (t * t - 1))) / (d * d)
        } else {
            val s = (1 - g * g) / (1 - g + 2 * g * u)
            ((1 + g * g - s * s) / (2 * g)).coerceIn(-1.0, 1.0)
        }
        val sine = sqrt(maxOf(0.0, 1 - cosine * cosine))
        val phi = 2 * PI * v
        val frame = Microfacets.frame(incoming)
        return incoming * cosine + (frame.tangent * cos(phi) + frame.bitangent * sin(phi)) * sine
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

    /** Weight of a scattering at [distance], normalized by the mixture of the three distance densities. */
    fun scatterWeight(medium: Medium, distance: Double): Color {
        val density = medium.density
        val rates = density * medium.transmittance(distance)
        val sum = rates.x + rates.y + rates.z
        val weights = if (sum >= java.lang.Double.MIN_NORMAL * CHANNELS && sum.isFinite()) {
            rates / (sum / CHANNELS)
        } else {
            // The normalized probabilities can be ordinary even when all three products round to zero.
            val lowest = minOf(positive(density.x), positive(density.y), positive(density.z))
            if (!lowest.isFinite() || !distance.isFinite()) return Color.ZERO
            fun logRate(rate: Double) =
                if (rate > 0.0) ln(rate) - (rate - lowest) * distance else Double.NEGATIVE_INFINITY
            val logs = Color(logRate(density.x), logRate(density.y), logRate(density.z))
            val largest = maxOf(logs.x, logs.y, logs.z)
            normalize(Color(exp(logs.x - largest), exp(logs.y - largest), exp(logs.z - largest)))
        }
        return medium.albedo * weights
    }

    /** Weight of passing [distance] through the medium untouched, normalized by its mixture probability. */
    fun passWeight(medium: Medium, distance: Double): Color {
        val transmission = medium.transmittance(distance)
        val sum = transmission.x + transmission.y + transmission.z
        if (sum >= java.lang.Double.MIN_NORMAL * CHANNELS) return transmission / (sum / CHANNELS)
        // Cancel the common extinction before exp when absolute channel probabilities lose precision.
        val density = medium.density
        val lowest = minOf(density.x, density.y, density.z)
        return if (distance.isFinite()) {
            normalize(Medium(density - lowest, Color.ZERO).transmittance(distance))
        } else Color.ZERO
    }

    private fun positive(rate: Double) = if (rate > 0.0) rate else Double.POSITIVE_INFINITY

    private fun normalize(values: Color): Color =
        values / ((values.x + values.y + values.z) / CHANNELS)
}
