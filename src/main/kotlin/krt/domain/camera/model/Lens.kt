package krt.domain.camera.model

import kotlin.math.roundToInt
import kotlin.math.tan
import krt.platform.math.Point3

/** Rules of the camera lens as the panel describes it. */
object Lens {

    /** Ready fields of view, in degrees. */
    val FOV_PRESETS = listOf("Fisheye" to 150.0, "Wide" to 90.0, "Normal" to 50.0, "Tele" to 20.0)

    /** Height of the frame the focal length is measured against, as a 35 mm film frame is 24 mm high. */
    private const val FRAME_HEIGHT_MM = 24.0
    private const val SUBTLE = 0.2
    private const val SOFT = 1.0
    private const val STRONG = 4.0

    /** Focal length, in mm, that gives a vertical field of view of [fovDegrees] on a 24 mm high frame. */
    fun focalLengthMm(fovDegrees: Double): Int =
        (FRAME_HEIGHT_MM / 2 / tan(Math.toRadians(fovDegrees) / 2)).roundToInt()

    /** How much an [aperture] blurs what is off the focus plane, in a word. */
    fun blur(aperture: Double): String = when {
        aperture < SUBTLE -> "subtle blur"
        aperture < SOFT -> "soft blur"
        aperture < STRONG -> "strong blur"
        else -> "extreme blur"
    }

    /** Focus distance that puts [point] in focus for a camera at [origin]. */
    fun focusOn(origin: Point3, point: Point3): Double = (point - origin).length()
}
