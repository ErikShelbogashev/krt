package krt.platform.math

import kotlin.math.abs

/** Discriminant of the ray/sphere quadratic with its linear coefficient divided by two. */
object SphereRoots {
    private const val CANCELLATION = 1e-8

    fun discriminant(offset: Point3, direction: Point3, a: Double, halfB: Double, radius: Double): Double {
        val square = halfB * halfB
        val difference = square - a * (offset.dot(offset) - radius * radius)
        if (abs(difference) > square * CANCELLATION) return difference
        // |o × d|² = |o|²|d|² − (o·d)². This form preserves the radius of a small distant sphere.
        val perpendicular = offset.cross(direction)
        return a * radius * radius - perpendicular.dot(perpendicular)
    }
}
