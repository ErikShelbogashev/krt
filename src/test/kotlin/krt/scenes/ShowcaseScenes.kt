package krt.scenes

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import krt.domain.scene.model.Dielectric
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.Interface
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Material
import krt.domain.scene.model.Medium
import krt.domain.scene.model.MediumInterface
import krt.domain.scene.model.Meshes
import krt.domain.scene.model.Pattern
import krt.domain.scene.model.PatternKind
import krt.domain.scene.model.Patterned
import krt.domain.scene.model.Pose
import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Sky
import krt.domain.scene.model.SoapBubble
import krt.domain.scene.model.Sphere
import krt.domain.scene.model.StartView
import krt.domain.scene.model.Traceable
import krt.domain.scene.model.Triangle
import krt.domain.scene.model.Velvet
import krt.platform.math.Color
import krt.platform.math.Point3

/** A scene as its builder makes it, to be written as a pbrt-v4 file under `krt/scenes`. */
class BuiltScene(val file: String, val objects: List<Traceable>, val look: SceneLook)

/**
 * The showcase scenes of the switcher, built from krt's own shapes and written once into pbrt-v4 files
 * ([ShowcaseScenesTest] keeps the files in step with this code). Each stays within the load rules of the showcase:
 * media only in small closed volumes, a few large lights, under 50 000 triangles, at most two fractals.
 */
object ShowcaseScenes {

    val ALL: List<BuiltScene> get() = listOf(prism(), bubbles())

    private fun p(x: Double, y: Double, z: Double) = Point3(x, y, z)
    private fun c(r: Double, g: Double, b: Double) = Color(r, g, b)

    /** A floor's pose: lying flat at height 0, its face up. */
    private val GROUND = Pose(Point3.ZERO, y = Point3(0.0, 0.0, -1.0), z = Point3(0.0, 1.0, 0.0))

    /** The same triangles under one name, so the file keeps them as one mesh and one light. */
    private fun named(id: String, triangles: List<Triangle>): List<Triangle> =
        triangles.map { Triangle(it.v0, it.v1, it.v2, it.material, id, it.normals, it.media) }

    /** An ellipsoid: an icosphere of [subdivisions] stretched by [radii] around [center], shaded smooth. */
    private fun ellipsoid(center: Point3, radii: Point3, subdivisions: Int, material: Material): List<Triangle> =
        Meshes.smooth(
            Meshes.icosphere(Pose(Point3.ZERO), 1.0, subdivisions, material).map {
                Triangle(center + it.v0 * radii, center + it.v1 * radii, center + it.v2 * radii, material)
            },
        )

    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Prism: a dark room, a lamp behind a vertical slit, a dispersive glass prism that fans the beam into a
     * rainbow on the back wall, and a thin haze only around the beam, so the beam shows.
     */
    fun prism(): BuiltScene {
        val black = Lambert(c(0.02, 0.02, 0.02))
        val wall = Lambert(c(0.75, 0.75, 0.73))
        val floor = Lambert(c(0.06, 0.06, 0.065))
        val glass = Dielectric(1.62, dispersion = 0.045)
        val haze = Medium.grey(0.2, c(0.95, 0.95, 0.95))
        val r = 0.45
        // Turned 35° from the beam, near the least deviation: the beam leaves at about 50°, violet to red over 17°.
        val turn = -35 * PI / 180
        val corners = listOf(PI / 2, 7 * PI / 6, 11 * PI / 6).map { p(r * cos(it + turn), 0.0, r * sin(it + turn)) }
        fun prismAt(y: Double) = corners.map { it + p(0.0, y, 0.0) }
        val (low, high) = prismAt(0.6) to prismAt(1.4)
        val prismSides = (0..2).flatMap { i ->
            val j = (i + 1) % 3
            listOf(
                Triangle(low[i], low[j], high[j], glass),
                Triangle(low[i], high[j], high[i], glass),
            )
        } + listOf(Triangle(low[0], low[2], low[1], glass), Triangle(high[0], high[1], high[2], glass))
        val slitX = -2.8
        val shutter = listOf(
            Meshes.box(Pose(p(slitX, 0.35, 0.0)), p(0.04, 0.35, 1.5), black),
            Meshes.box(Pose(p(slitX, 1.65, 0.0)), p(0.04, 0.35, 1.5), black),
            Meshes.box(Pose(p(slitX, 1.0, -0.8)), p(0.04, 0.3, 0.75), black),
            Meshes.box(Pose(p(slitX, 1.0, 0.8)), p(0.04, 0.3, 0.75), black),
            // A second slit further on keeps the beam narrow: a slit only wide enough lets light through both.
            Meshes.box(Pose(p(-1.45, 0.55, 0.0)), p(0.02, 0.25, 0.65), black),
            Meshes.box(Pose(p(-1.45, 1.45, 0.0)), p(0.02, 0.25, 0.65), black),
            Meshes.box(Pose(p(-1.45, 1.0, -0.36)), p(0.02, 0.2, 0.31), black),
            Meshes.box(Pose(p(-1.45, 1.0, 0.36)), p(0.02, 0.2, 0.31), black),
            // The lamp house: back, roof and sides, so the lamp lights the room only through the slit.
            Meshes.box(Pose(p(-3.3, 1.0, 0.0)), p(0.04, 1.0, 1.55), black),
            Meshes.box(Pose(p(-3.05, 2.04, 0.0)), p(0.29, 0.04, 1.6), black),
            Meshes.box(Pose(p(-3.05, 1.0, -1.6)), p(0.29, 1.0, 0.04), black),
            Meshes.box(Pose(p(-3.05, 1.0, 1.6)), p(0.29, 1.0, 0.04), black),
        ).flatten()
        val lamp = Meshes.panel(
            Pose(p(-3.0, 1.0, 0.0), x = p(0.0, 0.0, -1.0), y = p(0.0, 1.0, 0.0), z = p(1.0, 0.0, 0.0)), 0.12, 0.3,
            Emissive.kelvin(6500.0, 600.0),
        )
        val objects = buildList {
            addAll(named("Floor", Meshes.panel(GROUND, 5.0, 4.0, floor)))
            addAll(named("Slit", shutter))
            addAll(named("Lamp", lamp))
            addAll(named("Prism", prismSides))
            add(Sphere(p(0.0, 0.3, 0.0), 0.3, Lambert(c(0.05, 0.05, 0.05)), "Pedestal"))
            val hazeBox =
                Meshes.box(Pose(p(-0.6, 1.05, -0.9)), p(2.15, 0.7, 1.35), Interface, MediumInterface(haze, null))
            addAll(named("Haze", hazeBox))
        }
        val look = SceneLook(
            Sky(c(0.004, 0.004, 0.006), c(0.0, 0.0, 0.002)), fov = 48.0, aperture = 0.0, sampleLights = true,
            start = StartView(p(1.95, 1.25, 2.35), p(-0.15, 1.0, 0.0)),
        )
        return BuiltScene("prism", objects, look)
    }

    /**
     * Bubbles: soap bubbles of different film thicknesses over a velvet cushion on a dark table, one rising with
     * the shutter open; a window to one side and a ring light whose loop shows in every bubble.
     */
    fun bubbles(): BuiltScene {
        val velvet = Velvet(c(0.05, 0.14, 0.15), c(0.22, 0.4, 0.38), 0.7)
        val table = Patterned(
            Pattern(PatternKind.WOOD, c(0.22, 0.12, 0.06), c(0.08, 0.04, 0.02), 1.2, 0.35, 1.5, 5), 0.1, 0.008,
        )
        val bubbles = listOf(
            Triple(p(-0.9, 1.25, 0.2), 0.42, 380.0),
            Triple(p(0.15, 1.65, -0.35), 0.5, 470.0),
            Triple(p(1.05, 1.15, 0.35), 0.33, 560.0),
            Triple(p(-0.25, 0.95, 0.75), 0.24, 300.0),
            Triple(p(0.75, 2.3, -0.9), 0.3, 520.0),
        ).mapIndexed { i, (center, radius, thickness) ->
            Sphere(center, radius, SoapBubble(thickness), "Bubble ${i + 1}")
        } + Sphere(p(-0.55, 2.05, -0.6), 0.28, SoapBubble(420.0), "Rising bubble", velocity = p(0.0, 0.35, 0.0))
        val window = Meshes.panel(
            Pose(p(-3.6, 2.6, 3.2), x = p(0.0, 0.0, -1.0), y = p(0.0, 1.0, 0.0), z = p(1.0, 0.0, 0.0)), 1.6, 1.1,
            Emissive.kelvin(7000.0, 7.0),
        )
        val ring = Meshes.torus(
            Pose(p(0.4, 2.2, 6.4), x = p(1.0, 0.0, 0.0), y = p(0.0, 0.0, 1.0), z = p(0.0, -1.0, 0.0)),
            0.9, 0.06, 48, 8, Emissive.kelvin(4500.0, 9.0),
        )
        val objects = buildList {
            addAll(named("Table", Meshes.panel(GROUND, 6.0, 5.0, table)))
            addAll(named("Cushion", ellipsoid(p(0.0, 0.32, 0.0), p(1.25, 0.32, 0.9), 3, velvet)))
            addAll(bubbles)
            addAll(named("Window", window))
            addAll(named("Ring light", ring))
        }
        val look = SceneLook(
            Sky(c(0.05, 0.05, 0.07), c(0.01, 0.012, 0.02)), fov = 40.0, aperture = 0.03, sampleLights = true,
            motionBlur = true, start = StartView(p(0.6, 1.9, 5.2), p(0.0, 1.2, 0.0)),
        )
        return BuiltScene("bubbles", objects, look)
    }

}
