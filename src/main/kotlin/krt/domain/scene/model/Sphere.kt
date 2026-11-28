package krt.domain.scene.model

import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import krt.platform.math.Aabb
import krt.platform.math.Point3
import krt.platform.math.Ray
import krt.platform.math.SphereRoots

/**
 * A ball of [radius] around [center]. A ball with a [velocity] moves by it over the shutter's open time: at moment
 * t in [0, 1) of a ray it is at [center] + [velocity] × t, so it comes out blurred along its path (ADR 0013).
 * A ball with [media] bounds them: fog or the inside of a translucent ball (ADR 0014).
 */
class Sphere(
    val center: Point3,
    val radius: Double,
    val material: Material,
    override val id: String = "Sphere[${UUID.randomUUID()}]",
    val velocity: Point3 = Point3.ZERO,
    val media: MediumInterface? = null,
) : Traceable {

    private val moving = velocity != Point3.ZERO

    /** The centre at moment [time] of the shutter. */
    fun centerAt(time: Double): Point3 = if (moving) center + velocity * time else center

    override fun hit(ray: Ray, tMin: Double, tMax: Double): Hit? {
        val center = centerAt(ray.time)
        val oc = ray.origin - center
        val a = ray.direction.dot(ray.direction)
        val halfB = oc.dot(ray.direction)
        if (a == 0.0) return null
        val discriminant = SphereRoots.discriminant(oc, ray.direction, a, halfB, radius)

        if (discriminant < 0) {
            return null
        }

        val sqrtD = sqrt(discriminant)
        var root = (-halfB - sqrtD) / a
        if (root < tMin || root > tMax) {
            root = (-halfB + sqrtD) / a
            if (root < tMin || root > tMax) {
                return null
            }
        }
        val hitPoint = ray.at(root)
        val outwardNormal = (hitPoint - center) / radius
        return Hit.create(hitPoint, root, ray, outwardNormal, material, media)
    }

    override fun bounds(): Aabb {
        val extent = Point3(radius, radius, radius)
        val end = center + velocity
        val low = Point3(minOf(center.x, end.x), minOf(center.y, end.y), minOf(center.z, end.z))
        val high = Point3(maxOf(center.x, end.x), maxOf(center.y, end.y), maxOf(center.z, end.z))
        return Aabb(low - extent, high + extent)
    }

    override fun translate(offset: Point3): Sphere = Sphere(center + offset, radius, material, id, velocity, media)

    override fun linearize(coefficient: Double): List<LineSegment> {
        val segments = mutableListOf<LineSegment>()
        val steps = (coefficient * 10).toInt() // Adjust this multiplier for more or fewer segments

        // Generate horizontal circles
        for (i in 0 until steps) {
            val theta1 = Math.PI * 2 * i / steps
            val theta2 = Math.PI * 2 * (i + 1) / steps
            for (j in 0 until steps) {
                val phi = Math.PI * j / steps
                val p1 = center + Point3(
                    radius * cos(theta1) * sin(phi),
                    radius * sin(theta1) * sin(phi),
                    radius * cos(phi)
                )
                val p2 = center + Point3(
                    radius * cos(theta2) * sin(phi),
                    radius * sin(theta2) * sin(phi),
                    radius * cos(phi)
                )
                segments.add(LineSegment(p1, p2))
            }
        }

        // Generate vertical circles
        for (i in 0 until steps) {
            val theta = Math.PI * 2 * i / steps
            for (j in 0 until steps) {
                val phi1 = Math.PI * j / steps
                val phi2 = Math.PI * (j + 1) / steps
                val p1 = center + Point3(
                    radius * cos(theta) * sin(phi1),
                    radius * sin(theta) * sin(phi1),
                    radius * cos(phi1)
                )
                val p2 = center + Point3(
                    radius * cos(theta) * sin(phi2),
                    radius * sin(theta) * sin(phi2),
                    radius * cos(phi2)
                )
                segments.add(LineSegment(p1, p2))
            }
        }

        return segments
    }
}
