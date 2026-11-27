package krt.domain.scene.model

import krt.platform.math.Aabb
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

/**
 * Fog, smoke or haze filling a sphere: the [medium] inside a sphere of [Interface], which light crosses unchanged
 * (ADR 0014). Rays that enter it travel in the medium until they leave it again, into [outside].
 */
class Fog(
    val center: Point3,
    val radius: Double,
    val medium: Medium,
    override val id: String = "Fog[${java.util.UUID.randomUUID()}]",
    val outside: Medium? = null,
) : Traceable {

    /** Grey fog of one [density] for every channel, whose particles reflect [albedo]. */
    constructor(
        center: Point3,
        radius: Double,
        density: Double,
        albedo: Color,
        id: String = "Fog[${java.util.UUID.randomUUID()}]",
    ) : this(center, radius, Medium.grey(density, albedo), id)

    val boundary = Sphere(center, radius, Interface, id, media = MediumInterface(medium, outside))

    /** Whether [point] lies in the fog, so a camera there starts its rays in it. */
    fun contains(point: Point3): Boolean = (point - center).length() < radius

    override fun hit(ray: Ray, tMin: Double, tMax: Double): Hit? = boundary.hit(ray, tMin, tMax)

    override fun bounds(): Aabb = boundary.bounds()

    override fun translate(offset: Point3): Fog = Fog(center + offset, radius, medium, id, outside)

    override fun linearize(coefficient: Double): List<LineSegment> = boundary.linearize(coefficient)
}
