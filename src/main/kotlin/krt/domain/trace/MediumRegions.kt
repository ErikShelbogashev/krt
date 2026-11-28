package krt.domain.trace

import krt.domain.scene.model.SceneObjectEdits
import krt.domain.scene.model.ObjectOptics
import krt.domain.scene.model.Sphere
import krt.domain.scene.model.Traceable
import krt.domain.scene.model.Triangle
import krt.platform.math.Point3
import krt.platform.math.Ray

/** The immediately enclosing closed boundary for an optical edit. Only boundaries naming a medium participate. */
internal class MediumRegions(objects: List<Traceable>) {
    private val volumes = objects.filter { ObjectOptics.inside(it) != null || SceneObjectEdits.outside(it) != null }
        .groupBy { it.id }.mapValues { (_, parts) -> Bvh(parts.toTypedArray()) }

    fun ownerOf(part: Traceable): String? {
        // Sample the surface, since the centre of a hollow region can lie inside one of its children.
        val position = when (part) {
            is Triangle -> (part.v0 + part.v1 + part.v2) / TRIANGLE_CORNERS
            is Sphere -> part.center + Point3(part.radius, 0, 0)
            else -> part.bounds().let { Point3(it.max.x, it.center.y, it.center.z) }
        }
        val ray = Ray(position, DIRECTION)
        var nearest = Double.POSITIVE_INFINITY
        var owner: String? = null
        for ((id, volume) in volumes) {
            if (id == part.id) continue
            val hit = volume.hit(ray, Double.MIN_VALUE, nearest)
            if (hit != null && !hit.isFrontFace) {
                // From inside, the first crossing is an exit. The innermost containing volume exits first.
                nearest = hit.t
                owner = id
            }
        }
        return owner
    }

    private companion object {
        const val TRIANGLE_CORNERS = 3.0
        // Avoid tracing along a coordinate plane or through the diagonal seams of axis-aligned meshes.
        val DIRECTION = Point3(1.0, kotlin.math.sqrt(2.0), kotlin.math.sqrt(3.0))
    }
}
