package krt.domain.scene.model

import krt.platform.math.Point3
import krt.platform.math.Ray

interface Traceable {
    fun hit(ray: Ray, tMin: Double, tMax: Double): Hit?
    val id: String

    /** Returns a copy of this object moved by [offset]. */
    fun translate(offset: Point3): Traceable
    fun linearize(coefficient: Double): List<LineSegment>
}