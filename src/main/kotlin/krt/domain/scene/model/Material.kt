package krt.domain.scene.model

import kotlin.random.Random
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

/** How a scattered ray left the surface, so light sampling knows what the surface does with direct light. */
enum class Lobe {
    /** A mirror-like or refracted ray: direct light reaches it only along the ray itself. */
    SPECULAR,

    /** Lambertian: a diffuse surface lit by [Scattered.attenuation] / π times the cosine. */
    DIFFUSE,

    /** Isotropic scattering inside a medium: lit by [Scattered.attenuation] / 4π in every direction. */
    VOLUME,
}

data class Scattered(val ray: Ray, val attenuation: Color, val lobe: Lobe = Lobe.SPECULAR)

interface Material {
    fun scatter(ray: Ray, hit: Hit, random: Random): Scattered?
    fun emitted(u: Double, v: Double, p: Point3): Color {
        return Color(0.0, 0.0, 0.0) // Default to no emission
    }
}