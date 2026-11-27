package krt.domain.scene.model

import kotlin.random.Random
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

/** The scenes the window offers. */
enum class DemoScene(val title: String, val look: SceneLook) {
    SHOWCASE("Showcase", Showcase.LOOK),
    MATERIALS("Materials", MaterialsShowcase.LOOK),
    CLASSIC("Classic spheres", SceneLook.CLASSIC),
    ;

    /** The scene's objects; the default randomness lays the scene out the same way every time. */
    fun objects(random: Random = Random(LAYOUT_SEED)): List<Traceable> = when (this) {
        SHOWCASE -> Showcase.objects(random)
        MATERIALS -> MaterialsShowcase.objects()
        CLASSIC -> DemoScenes.classic(random)
    }

    companion object {
        private const val LAYOUT_SEED = 1

        /** The demo scene shown with [look], or null for a scene of one's own. */
        fun showing(look: SceneLook): DemoScene? = entries.find { it.look == look }
    }
}
