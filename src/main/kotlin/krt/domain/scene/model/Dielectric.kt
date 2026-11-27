package krt.domain.scene.model

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

/**
 * Glass with refraction index [indexOfReflection]. Tinted glass absorbs light inside it by Beer–Lambert: after a
 * unit of distance a [tint] channel keeps its own share to the power of [density]; clear glass has a white tint.
 */
class Dielectric(
    val indexOfReflection: Double,
    val tint: Color = CLEAR,
    val density: Double = 0.0,
) : Material {

    /** Absorption per unit of distance, per channel. */
    val absorption: Color = Color(absorb(tint.x), absorb(tint.y), absorb(tint.z))

    override fun scatter(ray: Ray, hit: Hit, random: Random): Scattered {
        val clear = hit.isFrontFace || density == 0.0
        val attenuation = if (clear) Point3.ONE else transmittance(hit.t * ray.direction.length())
        val refractionRatio = if (hit.isFrontFace) 1.0 / indexOfReflection else indexOfReflection

        val unitDirection = ray.direction.unit()
        val cosTheta = min(-unitDirection.dot(hit.normal), 1.0)
        val sinTheta = sqrt(1.0 - cosTheta * cosTheta)

        val cannotRefract = refractionRatio * sinTheta > 1.0
        val shouldReflect = cannotRefract || reflectance(cosTheta, refractionRatio) > random.nextDouble()
        val direction = if (shouldReflect) {
            unitDirection.reflect(hit.normal)
        } else {
            unitDirection.refract(hit.normal, refractionRatio)
        }

        return Scattered(Ray(hit.p, direction), attenuation)
    }

    /** Share of light left after [distance] inside the glass. */
    private fun transmittance(distance: Double) =
        Color(exp(-absorption.x * distance), exp(-absorption.y * distance), exp(-absorption.z * distance))

    private fun absorb(channel: Double): Double = ln(1.0 / channel.coerceIn(MIN_TINT, 1.0)) * density

    private fun reflectance(cosine: Double, refIdx: Double): Double {
        val r0 = ((1 - refIdx) / (1 + refIdx)).let { it * it }
        return r0 + (1 - r0) * (1 - cosine).pow(5)
    }

    private companion object {
        val CLEAR = Color(1.0, 1.0, 1.0)
        const val MIN_TINT = 1e-3
    }
}
