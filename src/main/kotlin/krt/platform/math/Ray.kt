package krt.platform.math

/**
 * A ray from [origin] along [direction], sent at moment [time] of the shutter, carrying light of one [wavelength]
 * in nanometres once something has split white light by wavelength, 0 before that.
 */
class Ray(
    val origin: Point3, val direction: Point3, val time: Double = 0.0, val wavelength: Double = 0.0,
    val pixel: PixelDifferentials? = null,
) {
    fun at(t: Double): Point3 = origin + direction * t

    /** The ray that goes on from [from] along [direction]: the same moment and wavelength. */
    fun next(from: Point3, direction: Point3): Ray = Ray(from, direction, time, wavelength)
}

/** Change in primary ray direction across a film pixel, or in hit position after tangent-plane projection. */
class PixelDifferentials(val dx: Point3, val dy: Point3, private val travelled: Double = 0.0) {
    /** Crossing a medium boundary preserves direction; parameters to successive boundaries add. */
    fun advanced(t: Double) = PixelDifferentials(dx, dy, travelled + t)

    fun scaled(width: Double): PixelDifferentials =
        if (width == 1.0) this else PixelDifferentials(dx * width, dy * width, travelled)

    fun onSurface(direction: Point3, normal: Point3, t: Double): PixelDifferentials {
        val incidence = direction.dot(normal)
        val limit = (direction.length() * 1e-7).coerceAtLeast(Double.MIN_VALUE)
        val denominator = if (incidence < 0.0) minOf(incidence, -limit) else maxOf(incidence, limit)
        fun project(d: Point3) = (d - direction * (d.dot(normal) / denominator)) * (travelled + t)
        return PixelDifferentials(project(dx), project(dy))
    }

    companion object {
        /** Width of one texture sample covering its share of a unit-area film pixel. */
        fun sampleWidth(samples: Int): Double = 1.0 / kotlin.math.sqrt(samples.coerceAtLeast(1).toDouble())
    }
}
