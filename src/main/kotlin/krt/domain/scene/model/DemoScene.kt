package krt.domain.scene.model

import krt.platform.math.Point3

/** Where the camera of a scene stands at the start and the point it looks at. */
data class StartView(val origin: Point3, val target: Point3)

/**
 * How a scene is shown: its [sky], the lens the camera starts with, whether light paths sample the scene's lights
 * directly ([sampleLights], next event estimation), which takes the noise out of small bright lights, whether
 * the shutter stays open for a while, so moving balls blur ([motionBlur]), and the view the camera starts with,
 * when the scene has one of its own ([start]); without it, the window's start view.
 */
data class SceneLook(
    val sky: Sky,
    val fov: Double,
    val aperture: Double,
    val sampleLights: Boolean = false,
    val motionBlur: Boolean = false,
    val start: StartView? = null,
) {
    companion object {
        val CLASSIC = SceneLook(Sky.DAYLIGHT, fov = 90.0, aperture = 0.1)
    }
}

/**
 * The scenes the window offers, each a scene file in the resources under `krt/scenes` ([path]): pbrt-v4 files of
 * krt's own and the OBJ scenes of the GitHub voxel package (ADR 0016).
 */
enum class DemoScene(val title: String, val path: String) {
    SHOWCASE("Showcase", "showcase.pbrt"),
    MATERIALS("Materials", "materials.pbrt"),
    FRACTALS("Fractals", "fractals.pbrt"),
    CLASSIC("Classic spheres", "classic.pbrt"),
}
