package krt.domain.camera.model

import krt.platform.math.Point3

/** A ready camera position: where it stands and the point it looks at. */
@Suppress("MagicNumber") // the coordinates are the presets
enum class ViewPreset(val title: String, val origin: Point3, val target: Point3) {
    /** The whole scene from afar; the window starts with it and Reset view returns to it. */
    OVERVIEW("Overview", Point3(13.0, 2.0, 3.0), Point3(0.0, 0.0, 0.0)),
    FRONT("Front", Point3(0.0, 1.0, 10.0), Point3(0.0, 1.0, 0.0)),
    TOP("Top", Point3(0.0, 12.0, 0.01), Point3(0.0, 0.0, 0.0)),

    /** The view of Camera.default, close to the centre of the scene. */
    CLOSE_UP("Close-up", Point3(1.0, 1.0, 1.0), Point3(0.0, 0.0, 0.0)),
    ;

    /** Focus distance that keeps the point the preset looks at sharp. */
    val focusDistance: Double get() = Lens.focusOn(origin, target)

    /**
     * True while the camera stands at this preset and looks its way. The camera keeps its pitch off the poles,
     * so a view straight down, like [TOP], counts as long as it is within a degree.
     */
    fun matches(origin: Point3, lookAt: Point3): Boolean =
        (origin - this.origin).length() < SAME_PLACE &&
            (lookAt - origin).unit().dot((target - this.origin).unit()) > SAME_DIRECTION

    companion object {
        private const val SAME_PLACE = 1e-6
        private const val SAME_DIRECTION = 0.9998

        /** The view the window starts with. */
        val START = OVERVIEW

        fun matching(origin: Point3, lookAt: Point3): ViewPreset? = entries.find { it.matches(origin, lookAt) }
    }
}
