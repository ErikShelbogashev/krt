package krt.domain.scene.model

import java.util.UUID
import krt.platform.math.Aabb
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

/**
 * A translucent ball, like wax, jade or marble: light refracts in through a glass-like [surface] of index [ior],
 * wanders inside, scattering off particles of [albedo] after free paths of 1 / [density] on average, and leaves
 * somewhere else (random-walk subsurface scattering, ADR 0013). The inside is [Fog] filling the same sphere: a ray
 * inside meets either a particle or the surface on its way out, whichever comes first.
 */
class Translucent(
    val center: Point3,
    val radius: Double,
    val albedo: Color,
    val density: Double,
    val ior: Double = WAX_IOR,
    val roughness: Double = 0.0,
    override val id: String = "Translucent[${UUID.randomUUID()}]",
) : Traceable {

    val surface = Sphere(center, radius, Dielectric(ior, roughness = roughness))
    val inside = Fog(center, radius, density, albedo)

    /** The objects it is made of, for the GPU, which has spheres and fog but no translucent balls. */
    val parts: List<Traceable> get() = listOf(surface, inside)

    override fun hit(ray: Ray, tMin: Double, tMax: Double): Hit? {
        val wall = surface.hit(ray, tMin, tMax)
        return inside.hit(ray, tMin, wall?.t ?: tMax) ?: wall
    }

    override fun bounds(): Aabb = surface.bounds()

    override fun translate(offset: Point3): Translucent =
        Translucent(center + offset, radius, albedo, density, ior, roughness, id)

    override fun linearize(coefficient: Double): List<LineSegment> = surface.linearize(coefficient)

    private companion object {
        const val WAX_IOR = 1.45
    }
}
