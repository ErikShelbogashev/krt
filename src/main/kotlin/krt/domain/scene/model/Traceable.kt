package krt.domain.scene.model

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
}