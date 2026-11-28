package krt.scenes

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import krt.domain.scene.model.Conductor
import krt.domain.scene.model.Dielectric
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.Fog
import krt.domain.scene.model.Fractal
import krt.domain.scene.model.FractalKind
import krt.domain.scene.model.GlowingPattern
import krt.domain.scene.model.Interface
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Material
import krt.domain.scene.model.Medium
import krt.domain.scene.model.MediumInterface
import krt.domain.scene.model.Meshes
import krt.domain.scene.model.Metals
import krt.domain.scene.model.Pattern
import krt.domain.scene.model.PatternKind
import krt.domain.scene.model.Patterned
import krt.domain.scene.model.Plastic
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

    val ALL: List<BuiltScene> get() = listOf(prism(), bubbles(), candle(), temple(), metals())

    private fun p(x: Double, y: Double, z: Double) = Point3(x, y, z)
    private fun c(r: Double, g: Double, b: Double) = Color(r, g, b)

    /** A floor's pose: lying flat at height 0, its face up. */
    private val GROUND = Pose(Point3.ZERO, y = Point3(0.0, 0.0, -1.0), z = Point3(0.0, 1.0, 0.0))

    /** The same triangles under one name, so the file keeps them as one mesh and one light. */
    private fun named(id: String, triangles: List<Triangle>): List<Triangle> =
        triangles.map { Triangle(it.v0, it.v1, it.v2, it.material, id, it.normals, it.media) }

    /**
     * A surface of revolution around the vertical axis through [base]: the [profile] of (radius, height) points
     * from bottom to top, [segments] around, its rings joined; closed at an end where the radius is zero.
     */
    private fun lathe(base: Point3, profile: List<Pair<Double, Double>>, segments: Int, material: Material,
                      media: MediumInterface? = null): List<Triangle> {
        fun at(ring: Int, step: Int): Point3 {
            val (radius, height) = profile[ring]
            val angle = 2 * PI * step / segments
            return base + p(radius * cos(angle), height, -radius * sin(angle))
        }
        val raw = (0 until profile.size - 1).flatMap { i ->
            (0 until segments).flatMap { j ->
                val a = at(i, j)
                val cc = at(i + 1, j + 1)
                listOfNotNull(
                    Triangle(a, at(i, j + 1), cc, material, media = media).takeIf { profile[i].first > 0 },
                    Triangle(a, cc, at(i + 1, j), material, media = media).takeIf { profile[i + 1].first > 0 },
                )
            }
        }
        return Meshes.smooth(raw).map { Triangle(it.v0, it.v1, it.v2, material, it.id, it.normals, media) }
    }

    /** A closed upright cylinder of [radius] and [height] standing on [base], its flat caps sharp. */
    private fun cylinder(base: Point3, radius: Double, height: Double, segments: Int, material: Material,
                         media: MediumInterface? = null): List<Triangle> {
        val side = lathe(base, listOf(radius to 0.0, radius to height), segments, material, media)
        val bottom = lathe(base, listOf(0.0 to 0.0, radius to 0.0), segments, material, media).map(::flat)
        val top = lathe(base, listOf(radius to height, 0.0 to height), segments, material, media).map(::flat)
        return side + bottom + top
    }

    private fun flat(t: Triangle) = Triangle(t.v0, t.v1, t.v2, t.material, t.id, null, t.media)

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
        val floor = Lambert(c(0.06, 0.06, 0.065))
        val glass = Dielectric(1.62, dispersion = 0.045)
        val haze = Medium.grey(0.07, c(0.95, 0.95, 0.95))
        val r = 0.6
        // Turned 35° from the beam, near the least deviation: the beam leaves at about 50°, violet to red over 17°.
        val turn = -35 * PI / 180
        val corners = listOf(PI / 2, 7 * PI / 6, 11 * PI / 6).map { p(r * cos(it + turn), 0.0, r * sin(it + turn)) }
        fun prismAt(y: Double) = corners.map { it + p(0.0, y, 0.0) }
        val (low, high) = prismAt(0.3) to prismAt(1.7)
        // Wound so each face looks out of the glass: the side a ray meets it from says whether it enters or leaves.
        val prismSides = (0..2).flatMap { i ->
            val j = (i + 1) % 3
            listOf(
                Triangle(low[i], high[j], low[j], glass),
                Triangle(low[i], high[i], high[j], glass),
            )
        } + listOf(Triangle(low[0], low[1], low[2], glass), Triangle(high[0], high[2], high[1], glass))
        // A slit narrow across and tall: the narrow way keeps the rainbow sharp, the tall way lets light through
        // over a wide angle, so paths from the screen find the lamp through the prism often and the rainbow settles.
        val slitX = -1.6
        val house = listOf(
            Meshes.box(Pose(p(slitX, 0.95, -0.595)), p(0.03, 0.95, 0.575), black),
            Meshes.box(Pose(p(slitX, 0.95, 0.595)), p(0.03, 0.95, 0.575), black),
            Meshes.box(Pose(p(-2.4, 0.95, 0.0)), p(0.03, 0.95, 1.15), black),
            Meshes.box(Pose(p(-2.0, 1.93, 0.0)), p(0.43, 0.03, 1.18), black),
            Meshes.box(Pose(p(-2.0, 0.95, -1.15)), p(0.43, 0.95, 0.03), black),
            Meshes.box(Pose(p(-2.0, 0.95, 1.15)), p(0.43, 0.95, 0.03), black),
        ).flatten()
        val lamp = Meshes.panel(
            Pose(p(-2.3, 1.0, 0.0), x = p(0.0, 0.0, -1.0), y = p(0.0, 1.0, 0.0), z = p(1.0, 0.0, 0.0)), 0.05, 0.5,
            Emissive.kelvin(6500.0, 400.0),
        )
        val objects = buildList {
            addAll(named("Floor", Meshes.panel(GROUND, 5.0, 4.0, floor)))
            addAll(named("Lamp house", house))
            addAll(named("Lamp", lamp))
            addAll(named("Prism", prismSides))
            val hazeBox =
                Meshes.box(Pose(p(-0.85, 1.0, 0.0)), p(0.72, 0.6, 0.35), Interface, MediumInterface(haze, null))
            addAll(named("Haze", hazeBox))
        }
        val look = SceneLook(
            Sky(c(0.004, 0.004, 0.006), c(0.0, 0.0, 0.002)), fov = 50.0, aperture = 0.0, sampleLights = true,
            // In the fanned beam, looking back: each colour leaves the prism towards the camera from another part
            // of its face, so the slit shows across the prism as a rainbow, lit directly, not by a caustic.
            start = StartView(p(2.0, 1.15, 1.95), p(0.0, 1.0, 0.0)),
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

    /**
     * Candle and jade: a wax candle whose flame glows like a black body at 1800 K, and a jade figure of spheres and a
     * ring beside it, both lit through from inside; nothing else lights the room.
     */
    fun candle(): BuiltScene {
        val wax = MediumInterface(Medium(c(2.2, 2.4, 3.0), c(0.995, 0.97, 0.9)), null)
        val jade = Medium(c(8.0, 1.6, 5.0), c(0.3, 0.96, 0.55))
        val table = Patterned(
            Pattern(PatternKind.WOOD, c(0.16, 0.08, 0.04), c(0.05, 0.025, 0.012), 1.0, 0.4, 2.0, 5), 0.15, 0.01,
        )
        val flameColour = Emissive.kelvin(1800.0, 1.0).emitColor
        val flame = GlowingPattern(
            Pattern(PatternKind.CLOUDS, flameColour * 0.35, flameColour, 6.0, 0.8, 1.0, 4), 30.0,
        )
        val flameProfile = listOf(0.0 to 0.0, 0.045 to 0.04, 0.065 to 0.1, 0.058 to 0.18, 0.035 to 0.26, 0.0 to 0.34)
        val objects = buildList {
            addAll(named("Table", Meshes.panel(GROUND, 3.0, 2.0, table)))
            // Wax and jade bound their media with an interface, not glass: light from the flame reaches the
            // particles inside by light sampling, so the glow from within settles in seconds, not minutes.
            addAll(named("Candle", cylinder(p(-0.45, 0.0, 0.0), 0.16, 0.7, 48, Interface, wax)))
            addAll(named("Wick", cylinder(p(-0.45, 0.7, 0.0), 0.008, 0.06, 8, Lambert(c(0.02, 0.02, 0.02)))))
            addAll(named("Flame", lathe(p(-0.45, 0.74, 0.0), flameProfile, 24, flame)))
            addAll(named("Warm fill", Meshes.panel(
                Pose(p(-2.2, 1.6, 1.6), x = p(-0.6, 0.0, -0.8), y = p(0.0, 1.0, 0.0), z = p(0.8, 0.0, -0.6)), 0.9, 0.6,
                Emissive.kelvin(2700.0, 0.35),
            )))
            add(Fog(p(0.45, 0.32, 0.1), 0.32, jade, "Jade body"))
            add(Fog(p(0.45, 0.78, 0.1), 0.17, jade, "Jade head"))
            add(Fog(p(0.85, 0.12, 0.35), 0.12, jade, "Jade pebble"))
            addAll(named("Jade ring", Meshes.smooth(Meshes.torus(
                Pose(p(0.45, 0.78, 0.1), x = p(1.0, 0.0, 0.0), y = p(0.0, 0.0, 1.0), z = p(0.0, -1.0, 0.0)),
                0.27, 0.025, 48, 10, Interface,
            )).map { Triangle(it.v0, it.v1, it.v2, it.material, it.id, it.normals, MediumInterface(jade, null)) }))
        }
        val look = SceneLook(
            Sky(c(0.004, 0.004, 0.008), c(0.001, 0.001, 0.003)), fov = 34.0, aperture = 0.02, sampleLights = true,
            start = StartView(p(0.2, 1.0, 2.6), p(0.0, 0.5, 0.0)),
        )
        return BuiltScene("candle", objects, look)
    }

    /**
     * Fractal temple: a Menger sponge of three levels in a ball of thin haze, a low sun at 3000 K behind it whose
     * light falls through the holes in shafts, and sand underfoot.
     */
    fun temple(): BuiltScene {
        val stone = Lambert(c(0.62, 0.5, 0.38))
        val sand = Lambert(c(0.5, 0.38, 0.25))
        val haze = Medium.grey(0.17, c(0.92, 0.9, 0.86))
        val objects = listOf(
            Sphere(p(0.0, -1000.0, 0.0), 1000.0, sand, "Sand"),
            Fractal(FractalKind.MENGER, p(0.0, 1.45, 0.0), 1.45, stone, "Menger temple", iterations = 3),
            Fog(p(0.0, 1.2, 0.0), 4.2, haze, "Haze"),
            Sphere(p(-30.0, 3.5, 45.0), 4.0, Emissive.kelvin(3000.0, 170.0), "Sun"),
        )
        val look = SceneLook(
            Sky(c(0.09, 0.05, 0.035), c(0.01, 0.012, 0.03)), fov = 42.0, aperture = 0.0, sampleLights = true,
            start = StartView(p(5.2, 1.4, 3.4), p(-0.3, 1.5, -0.2)),
        )
        return BuiltScene("temple", objects, look)
    }

    /**
     * Metals: gold and copper by their complex refraction index, a brushed aluminium disk and a brushed can,
     * frosted glass and red car paint under a clear coat, on a grey studio sweep under three softboxes.
     */
    fun metals(): BuiltScene {
        val grey = Lambert(c(0.42, 0.42, 0.43))
        val paint = Plastic(c(0.55, 0.02, 0.03), 0.02)
        val objects = buildList {
            addAll(named("Studio sweep", studioSweep(grey)))
            add(Sphere(p(-1.5, 0.55, 0.3), 0.55, Conductor(Metals.GOLD, 0.08), "Gold"))
            add(Sphere(p(1.55, 0.5, 0.45), 0.5, Conductor(Metals.COPPER, 0.18), "Copper"))
            add(Sphere(p(0.35, 0.42, 1.25), 0.42, Dielectric(1.5, roughness = 0.25), "Frosted glass"))
            val (disk, can) = Conductor(Metals.ALUMINIUM, 0.3, 0.85) to Conductor(Metals.ALUMINIUM, 0.22, 0.85)
            addAll(named("Brushed disk", cylinder(p(0.0, 0.0, -0.4), 0.9, 0.05, 96, disk)))
            addAll(named("Brushed can", cylinder(p(-0.25, 0.05, -0.55), 0.3, 0.75, 64, can)))
            addAll(named("Car paint", Meshes.smooth(Meshes.torusKnot(
                Pose(p(0.45, 0.62, -0.1)), 0.17, 0.09, 2, 3, 96, 10, paint,
            ))))
            addAll(named("Softbox left", softbox(p(-3.2, 2.6, 2.0), p(1.0, -0.6, -0.6), 5500.0, 7.0)))
            addAll(named("Softbox right", softbox(p(3.4, 2.2, 1.4), p(-1.0, -0.5, -0.4), 5500.0, 5.0)))
            addAll(named("Softbox top", softbox(p(0.0, 4.2, -0.6), p(0.0, -1.0, 0.1), 6000.0, 4.0)))
        }
        val look = SceneLook(
            Sky(c(0.02, 0.02, 0.022), c(0.01, 0.01, 0.012)), fov = 38.0, aperture = 0.015, sampleLights = true,
            start = StartView(p(0.2, 2.0, 5.6), p(0.0, 0.45, 0.0)),
        )
        return BuiltScene("metals", objects, look)
    }

    /** A floor that bends up into the back wall: the seamless backdrop of a studio. */
    private fun studioSweep(material: Material): List<Triangle> {
        val bend = (0..12).map { i ->
            val a = PI / 2 * i / 12
            p(0.0, 1.5 - 1.5 * cos(a), -2.0 - 1.5 * sin(a))
        }
        val path = listOf(p(0.0, 0.0, 4.0)) + bend + listOf(p(0.0, 5.0, -3.5))
        val half = 6.0
        val raw = path.zipWithNext { a, b ->
            val side = p(half, 0.0, 0.0)
            listOf(Triangle(a - side, a + side, b + side, material), Triangle(a - side, b + side, b - side, material))
        }.flatten()
        return Meshes.smooth(raw)
    }

    /** A square softbox [center]ed, facing [facing], glowing at [kelvin]. */
    private fun softbox(center: Point3, facing: Point3, kelvin: Double, intensity: Double): List<Triangle> {
        val z = facing.unit()
        val x = p(0.0, 1.0, 0.0).cross(z).let { if (it.length() < 1e-6) p(1.0, 0.0, 0.0) else it.unit() }
        val y = z.cross(x)
        return Meshes.panel(Pose(center, x, y, z), 0.8, 0.8, Emissive.kelvin(kelvin, intensity))
    }
}
