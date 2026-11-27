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
 *
 * Glass with [dispersion] bends short waves more than long ones (Cauchy: the index grows by [dispersion] µm² per
 * µm⁻² of wavelength, from [indexOfReflection] at the yellow d line), so it splits white light into a rainbow:
 * white light that meets it goes on as light of one random wavelength (ADR 0013).
 *
 * Glass of [roughness] above 0 is frosted: GGX microfacets scatter the light it reflects and lets through.
 */
class Dielectric(
    val indexOfReflection: Double,
    val tint: Color = CLEAR,
    val density: Double = 0.0,
    val dispersion: Double = 0.0,
    val roughness: Double = 0.0,
) : Material {

    val facets = Microfacets.of(roughness)

    /** Absorption per unit of distance, per channel. */
    val absorption: Color = Color(absorb(tint.x), absorb(tint.y), absorb(tint.z))

    override fun scatter(ray: Ray, hit: Hit, random: Random): Scattered? {
        val split = dispersion != 0.0 && ray.wavelength == 0.0
        val wavelength = if (split) Spectrum.sample(random.nextDouble()) else ray.wavelength
        val clear = hit.isFrontFace || density == 0.0
        val absorbed = if (clear) Point3.ONE else transmittance(hit.t * ray.direction.length())
        val attenuation = if (split) absorbed * Spectrum.weight(wavelength) else absorbed
        val index = indexAt(wavelength)
        val refractionRatio = if (hit.isFrontFace) 1.0 / index else index

        val unitDirection = ray.direction.unit()
        val frame = if (roughness > 0) Microfacets.frame(hit.normal) else null
        val normal = frame?.let { it.world(facets.visibleNormal(it.local(-unitDirection), random)) } ?: hit.normal
        val shouldReflect = reflects(unitDirection, normal, refractionRatio, random)
        val direction = if (shouldReflect) {
            unitDirection.reflect(normal)
        } else {
            unitDirection.refract(normal, refractionRatio)
        }
        val shadowing = frame?.let { frosted(it, unitDirection, direction, shouldReflect) } ?: 1.0
        if (shadowing <= 0) return null
        return Scattered(Ray(hit.p, direction, ray.time, wavelength), attenuation * shadowing)
    }

    /** Whether light along [unit] reflects off [normal]: always past the critical angle, else by Fresnel. */
    private fun reflects(unit: Point3, normal: Point3, refractionRatio: Double, random: Random): Boolean {
        val cosTheta = min(-unit.dot(normal), 1.0)
        val sinTheta = sqrt(1.0 - cosTheta * cosTheta)
        return refractionRatio * sinTheta > 1.0 || reflectance(cosTheta, refractionRatio) > random.nextDouble()
    }

    /** The microfacet shadowing of frosted glass, or 0 when the facet sent the ray to the wrong side of the surface. */
    private fun frosted(frame: Microfacets.Frame, unit: Point3, direction: Point3, reflected: Boolean): Double {
        val incoming = frame.local(direction)
        val wrongSide = if (reflected) incoming.z <= 0 else incoming.z >= 0
        return if (wrongSide) 0.0 else facets.shadowing(frame.local(-unit), incoming)
    }

    /** Refraction index for light of [wavelength] nm; white light (0) and glass without dispersion get the base one. */
    fun indexAt(wavelength: Double): Double {
        if (dispersion == 0.0 || wavelength == 0.0) return indexOfReflection
        val micrometres = wavelength / NM_PER_UM
        return indexOfReflection + dispersion * (1 / (micrometres * micrometres) - 1 / (D_LINE_UM * D_LINE_UM))
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
        const val NM_PER_UM = 1000.0
        const val D_LINE_UM = 0.5876
    }
}
