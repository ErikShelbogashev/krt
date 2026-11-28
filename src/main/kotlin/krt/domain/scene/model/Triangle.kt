package krt.domain.scene.model

import java.util.UUID
import krt.platform.math.Aabb
import krt.platform.math.Point3
import krt.platform.math.Ray

/** Unit normals at the three corners of a triangle, blended across it so a mesh of flat faces shades smoothly. */
data class VertexNormals(val n0: Point3, val n1: Point3, val n2: Point3) {
    /** The normal at barycentric coordinates [u] (towards the second corner) and [v] (towards the third). */
    fun at(u: Double, v: Double, fallback: Point3 = n0): Point3 {
        val sum = n0 * (1 - u - v) + n1 * u + n2 * v
        val length = sum.length()
        return if (length > 0.0) sum / length else fallback
    }
}

/**
 * A flat triangle. With [normals] it shades as if curved (ADR 0013): hits keep the side of the flat face, but their
 * normal is blended from the corners, turned to the face's side when the corners give it the other way.
 * Triangles of a closed mesh with [media] bound them (ADR 0014); the outward side is the side of the winding.
 */
class Triangle(
    val v0: Point3,
    val v1: Point3,
    val v2: Point3,
    val material: Material,
    override val id: String = "Triangle[${UUID.randomUUID()}]",
    val normals: VertexNormals? = null,
    val media: MediumInterface? = null,
) : Traceable {

    private val edge1 = v1 - v0
    private val edge2 = v2 - v0
    private val outwardNormal = edge1.cross(edge2).unit()

    override fun hit(ray: Ray, tMin: Double, tMax: Double): Hit? {
        val h = ray.direction.cross(edge2)
        val a = edge1.dot(h)

        if (a == 0.0) {
            return null
        }

        val f = 1.0 / a
        val s = ray.origin - v0
        val u = ratio(s.dot(h), a, f)

        if (u !in 0.0..1.0) {
            return null
        }

        val q = s.cross(edge1)
        val v = ratio(ray.direction.dot(q), a, f)

        if (!(v >= 0.0 && u + v <= 1.0)) {
            return null
        }

        val t = ratio(edge2.dot(q), a, f)
        if (!t.isFinite() || t !in tMin..tMax) {
            return null
        }

        val hitPoint = ray.at(t)
        val smooth = normals ?: return Hit.create(hitPoint, t, ray, outwardNormal, material, media)
        val isFrontFace = ray.direction.dot(outwardNormal) < 0
        val blended = smooth.at(u, v, outwardNormal)
        // Vertex normals given against the winding still shade the face's own side.
        val shading = if (blended.dot(outwardNormal) < 0) -blended else blended
        return Hit(hitPoint, t, if (isFrontFace) shading else -shading, isFrontFace, material, media,
            geometricNormal = if (isFrontFace) outwardNormal else -outwardNormal)
    }

    override fun bounds(): Aabb = Aabb(
        Point3(minOf(v0.x, v1.x, v2.x), minOf(v0.y, v1.y, v2.y), minOf(v0.z, v1.z, v2.z)),
        Point3(maxOf(v0.x, v1.x, v2.x), maxOf(v0.y, v1.y, v2.y), maxOf(v0.z, v1.z, v2.z)),
    )

    override fun translate(offset: Point3): Triangle =
        Triangle(v0 + offset, v1 + offset, v2 + offset, material, id, normals, media)

    override fun linearize(coefficient: Double): List<LineSegment> {
        return listOf(
            LineSegment(v0, v1),
            LineSegment(v1, v2),
            LineSegment(v2, v0)
        )
    }
}

/** A subnormal determinant can have an infinite reciprocal and a finite quotient. */
private fun ratio(numerator: Double, determinant: Double, reciprocal: Double): Double =
    if (reciprocal.isFinite()) numerator * reciprocal else numerator / determinant
