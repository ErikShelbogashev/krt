package raytracer.model

interface Traceable {
    fun hit(ray: Ray, tMin: Double, tMax: Double): Hit?
    val id: String

    /** Returns a copy of this object moved by [offset]. */
    fun translate(offset: Point3): Traceable
    fun linearize(coefficient: Double): List<LineSegment>
}