package krt.domain.scene.model

import kotlin.math.abs
import krt.platform.math.Aabb
import krt.platform.math.Point3
import krt.platform.math.Ray

interface Traceable {
    fun hit(ray: Ray, tMin: Double, tMax: Double): Hit?
    val id: String

    /** Box that contains the object; the trace kernel builds its BVH from these (ADR 0008). */
    fun bounds(): Aabb


    /** Returns a copy of this object moved by [offset]. */
    fun translate(offset: Point3): Traceable
    fun linearize(coefficient: Double): List<LineSegment>

    companion object {
        /** Rare fallback when squaring a finite direction underflows or overflows the intersection equation. */
        internal fun withScaledDirection(shape: Traceable, ray: Ray, tMin: Double, tMax: Double): Hit? {
            val d = ray.direction
            val scale = maxOf(abs(d.x), abs(d.y), abs(d.z))
            if (!(scale > 0.0) || !scale.isFinite()) return null
            val scaled = ray.next(ray.origin, d / scale)
            return shape.hit(scaled, tMin * scale, tMax * scale)?.let { hit ->
                val t = hit.t / scale
                if (t.isFinite() && t in tMin..tMax) hit.copy(t = t) else null
            }
        }
    }
}