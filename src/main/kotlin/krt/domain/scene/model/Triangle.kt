package krt.domain.scene.model

import java.util.UUID
import krt.platform.math.Aabb
import krt.platform.math.Point3
import krt.platform.math.Ray

class Triangle(
    private val v0: Point3,
    private val v1: Point3,
    private val v2: Point3,
    val material: Material,
    override val id: String = "Triangle[${UUID.randomUUID()}]",
) : Traceable {

    private val edge1 = v1 - v0
    private val edge2 = v2 - v0
    private val outwardNormal = edge1.cross(edge2).unit()

    override fun hit(ray: Ray, tMin: Double, tMax: Double): Hit? {
        val h = ray.direction.cross(edge2)
        val a = edge1.dot(h)

        if (a > -1e-10 && a < 1e-10) {
            return null
        }

        val f = 1.0 / a
        val s = ray.origin - v0
        val u = f * (s.dot(h))

        if (u < 0.0 || u > 1.0) {
            return null
        }

        val q = s.cross(edge1)
        val v = f * ray.direction.dot(q)

        if (v < 0.0 || u + v > 1.0) {
            return null
        }

        val t = f * edge2.dot(q)
        if (t < tMin || t > tMax) {
            return null
        }

        val hitPoint = ray.at(t)
        return Hit.create(hitPoint, t, ray, outwardNormal, material)
    }

    override fun bounds(): Aabb = Aabb(
        Point3(minOf(v0.x, v1.x, v2.x), minOf(v0.y, v1.y, v2.y), minOf(v0.z, v1.z, v2.z)),
        Point3(maxOf(v0.x, v1.x, v2.x), maxOf(v0.y, v1.y, v2.y), maxOf(v0.z, v1.z, v2.z)),
    )

    override fun translate(offset: Point3): Triangle = Triangle(v0 + offset, v1 + offset, v2 + offset, material, id)

    override fun linearize(coefficient: Double): List<LineSegment> {
        return listOf(
            LineSegment(v0, v1),
            LineSegment(v1, v2),
            LineSegment(v2, v0)
        )
    }
}