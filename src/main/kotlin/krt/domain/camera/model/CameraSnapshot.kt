package krt.domain.camera.model

import krt.platform.math.Point3

/** What the camera panel shows of the camera at one moment: where it is, where it looks and its lens. */
data class CameraSnapshot(
    val origin: Point3,
    val lookAt: Point3,
    val fov: Double,
    val aperture: Double,
    val focusDistance: Double,
    val movementSpeed: Double,
) {
    val preset: ViewPreset? get() = ViewPreset.matching(origin, lookAt)
}
