package krt.domain.scene.model

import kotlin.random.Random

/** How a scene is shown: its [sky] and the lens the camera starts with. */
data class SceneLook(val sky: Sky, val fov: Double, val aperture: Double) {
    companion object {
        val CLASSIC = SceneLook(Sky.DAYLIGHT, fov = 90.0, aperture = 0.1)
    }
}

/** The scenes the window offers. */
enum class DemoScene(val title: String, val look: SceneLook) {
    SHOWCASE("Showcase", Showcase.LOOK),
    CLASSIC("Classic spheres", SceneLook.CLASSIC),
    ;

    /** The scene's objects; the default randomness lays the scene out the same way every time. */
    fun objects(random: Random = Random(LAYOUT_SEED)): List<Traceable> = when (this) {
        SHOWCASE -> Showcase.objects(random)
        CLASSIC -> DemoScenes.classic(random)
    }

    companion object {
        private const val LAYOUT_SEED = 1

        /** The demo scene shown with [look], or null for a scene of one's own. */
        fun showing(look: SceneLook): DemoScene? = entries.find { it.look == look }
    }
}
