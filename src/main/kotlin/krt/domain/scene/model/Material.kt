package krt.domain.scene.model

import kotlin.random.Random
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

/**
 * How a scattered ray left the surface, so light sampling knows what the surface does with direct light and
 * whether it samples the lights there at all ([samplesLights]).
 */
enum class Lobe(val samplesLights: Boolean) {
    /** A mirror-like or refracted ray: direct light reaches it only along the ray itself. */
    SPECULAR(samplesLights = false),

    /**
     * Diffuse: the ray goes from the normal to a random point in the unit ball around its tip, as [Lambert] does;
     * the surface is lit by [Scattered.attenuation] times the density of such directions, 2 cos³ / π.
     */
    DIFFUSE(samplesLights = true),

    /** Isotropic scattering in a medium: lit by [Scattered.attenuation] / 4π in every direction. */
    VOLUME(samplesLights = true),

    /**
     * Isotropic scattering in a medium closed in glass or another solid surface, as in a [Translucent] ball: every
     * shadow ray would end on that surface, so the lights are not sampled there and the path goes on as after a
     * specular bounce.
     */
    ENCLOSED_VOLUME(samplesLights = false),
}

/**
 * A ray that goes on from a surface, with the share of light it carries back. [normal], when the material bent the
 * surface's normal (bump mapping), is the one direct light is weighed by.
 */
data class Scattered(val ray: Ray, val attenuation: Color, val lobe: Lobe = Lobe.SPECULAR, val normal: Point3? = null)

interface Material {
    fun scatter(ray: Ray, hit: Hit, random: Random): Scattered?
    fun emitted(u: Double, v: Double, p: Point3): Color {
        return Color(0.0, 0.0, 0.0) // Default to no emission
    }

    /** Whether the material gives off light, so light sampling treats objects of it as lights (ADR 0012). */
    val emits: Boolean get() = false
}