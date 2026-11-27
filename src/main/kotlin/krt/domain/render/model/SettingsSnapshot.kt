package krt.domain.render.model

import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Traceable
import krt.platform.math.Point3

/** Everything a benchmark changes, as it was before, so it can be put back. */
data class SettingsSnapshot(
    val objects: List<Traceable>,
    val look: SceneLook,
    val mode: RenderMode,
    val quality: FrameQuality,
    val view: ViewState,
)

/** Size, samples and depth of a frame. */
data class FrameQuality(val width: Int, val height: Int, val samples: Int, val depth: Int)

/** Where the camera stands and looks, and its lens. */
data class ViewState(
    val origin: Point3,
    val lookAt: Point3,
    val fov: Double,
    val aperture: Double,
    val focusDistance: Double,
)
