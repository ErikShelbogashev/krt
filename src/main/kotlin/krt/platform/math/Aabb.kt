package krt.platform.math

/** Axis-aligned bounding box from [min] to [max] corner. */
class Aabb(val min: Point3, val max: Point3) {
    val center: Point3 get() = (min + max) * HALF

    private companion object {
        const val HALF = 0.5
    }
}
