package krt.platform.math

/**
 * A ray from [origin] along [direction], sent at moment [time] of the shutter, carrying light of one [wavelength]
 * in nanometres once something has split white light by wavelength, 0 before that.
 */
class Ray(val origin: Point3, val direction: Point3, val time: Double = 0.0, val wavelength: Double = 0.0) {
    fun at(t: Double): Point3 = origin + direction * t

    /** The ray that goes on from [from] along [direction]: the same moment and wavelength. */
    fun next(from: Point3, direction: Point3): Ray = Ray(from, direction, time, wavelength)
}
