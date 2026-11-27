package krt.domain.scene.model

import java.util.UUID
import krt.platform.math.Aabb
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

/**
 * A translucent ball, like wax, jade or marble: light refracts in through a glass-like surface of index [ior],
 * wanders inside the [medium], scattering off its particles, and leaves somewhere else (random-walk subsurface
 * scattering, ADR 0013 and 0014). The ball bounds the medium; leaving it, light goes into [outside].
 */
class Translucent(
    val center: Point3,
    val radius: Double,
    val medium: Medium,
    val ior: Double = WAX_IOR,
    val roughness: Double = 0.0,
    override val id: String = "Translucent[${UUID.randomUUID()}]",
    val outside: Medium? = null,
) : Traceable {

    /** A ball of grey [density] whose particles reflect [albedo]. */
    constructor(
        center: Point3,
        radius: Double,
        albedo: Color,
        density: Double,
        ior: Double = WAX_IOR,
        roughness: Double = 0.0,
        id: String = "Translucent[${UUID.randomUUID()}]",
    ) : this(center, radius, Medium.grey(density, albedo), ior, roughness, id)

    val surface =
        Sphere(center, radius, Dielectric(ior, roughness = roughness), id, media = MediumInterface(medium, outside))

    override fun hit(ray: Ray, tMin: Double, tMax: Double): Hit? = surface.hit(ray, tMin, tMax)

    override fun bounds(): Aabb = surface.bounds()

    override fun translate(offset: Point3): Translucent =
        Translucent(center + offset, radius, medium, ior, roughness, id, outside)

    override fun linearize(coefficient: Double): List<LineSegment> = surface.linearize(coefficient)

    private companion object {
        const val WAX_IOR = 1.45
    }
}
