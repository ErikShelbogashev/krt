package krt.domain.scene.model

import krt.platform.math.Color
import krt.platform.math.Point3

/**
 * A scene to show off the physical materials at nightfall (ADR 0013): a crystal of dispersive glass on a polished
 * floor of procedural wood, gold, brushed aluminium and a smooth copper knot, jade, a wax ball with a candle flame,
 * velvet, an iridescent shell, frosted glass, a ball of lava, a cloudy planet, soap bubbles and two balls caught in
 * motion, lit by a warm and a cold panel by colour temperature in haze and a daylight panel overhead. It is laid
 * out for the overview view, from (13, 2, 3) towards the centre; everything in it is procedural.
 */
@Suppress("MagicNumber") // positions, sizes and colours are the scene
object MaterialsShowcase {

    private val NIGHTFALL = Sky(horizon = Color(0.32, 0.24, 0.3), zenith = Color(0.02, 0.03, 0.09))

    val LOOK = SceneLook(NIGHTFALL, fov = 34.0, aperture = 0.04, sampleLights = true, motionBlur = true)

    /** Across the view of the overview, to the right on screen. */
    private val RIGHT = Point3(0.222, 0.0, -0.963).unit()

    /** Along the view of the overview, towards the camera. */
    private val TOWARDS = Point3(0.974, 0.0, 0.225).unit()
    private val UP = Point3(0.0, 1.0, 0.0)

    private fun at(right: Double, up: Double, towards: Double) = RIGHT * right + UP * up + TOWARDS * towards

    fun objects(): List<Traceable> = buildList {
        add(Sphere(Point3(0.0, -1000.0, 0.0), 1000.0, FLOOR, "Wooden floor"))
        addAll(metals())
        addAll(soft())
        addAll(glass())
        addAll(lights())
        addAll(moving())
    }

    private fun metals(): List<Traceable> = buildList {
        add(Sphere(at(-2.9, 0.9, -0.4), 0.9, Conductor(Metals.GOLD, roughness = 0.25), "Gold"))
        add(Sphere(at(2.7, 0.8, -0.2), 0.8, Conductor(Metals.ALUMINIUM, 0.35, anisotropy = 0.85), "Brushed aluminium"))
        addAll(
            Meshes.smooth(
                Meshes.torusKnot(
                    Pose(at(-0.6, 1.5, -3.3), x = RIGHT, y = TOWARDS, z = UP * -1.0),
                    scale = 0.3,
                    tube = 0.2,
                    p = 2,
                    q = 3,
                    segments = 160,
                    sides = 12,
                    material = Conductor(Metals.COPPER, roughness = 0.15),
                ),
            ),
        )
    }

    private fun soft(): List<Traceable> = buildList {
        add(Translucent(at(1.4, 0.6, 2.0), 0.6, Color(0.45, 0.92, 0.6), density = 6.0, roughness = 0.1, id = "Jade"))
        add(Translucent(at(-1.5, 0.45, 2.3), 0.45, Color(0.995, 0.97, 0.9), density = 5.0, id = "Wax"))
        add(Sphere(at(-1.5, 1.05, 2.3), 0.07, Emissive.kelvin(1900.0, 30.0), "Candle flame"))
        add(Sphere(at(3.0, 0.55, 1.7), 0.55, Velvet(Color(0.32, 0.02, 0.08), Color(1.0, 0.55, 0.65), 0.45), "Velvet"))
        add(Sphere(at(-3.2, 0.5, 1.6), 0.5, Iridescent(Color(0.02, 0.03, 0.03), 480.0, 1.8, 0.05), "Beetle shell"))
        add(
            Sphere(
                at(4.1, 0.5, -2.4), 0.5,
                GlowingPattern(Pattern(PatternKind.NOISE, Color(1.0, 0.32, 0.02), Color(0.12, 0.01, 0.0), 2.2), 4.0),
                "Lava",
            ),
        )
        add(
            Sphere(
                at(-4.4, 0.6, -2.1), 0.6,
                Patterned(Pattern(PatternKind.CLOUDS, Color(0.1, 0.3, 0.75), Color(0.95, 0.95, 0.97), 1.6), 0.15),
                "Cloudy planet",
            ),
        )
    }

    private fun glass(): List<Traceable> = buildList {
        addAll(Meshes.icosphere(Pose(at(0.0, 1.05, 0.0)), 1.05, 0, Dielectric(1.55, dispersion = 0.07)))
        add(Sphere(at(0.7, 0.4, 3.3), 0.4, Dielectric(1.5, roughness = 0.35), "Frosted glass"))
        add(Sphere(at(0.9, 2.7, 0.9), 0.45, SoapBubble(thickness = 380.0), "Bubble"))
        add(Sphere(at(-1.1, 3.0, 0.4), 0.3, SoapBubble(thickness = 460.0), "Small bubble"))
        add(Sphere(at(1.9, 3.3, -0.8), 0.25, SoapBubble(thickness = 520.0), "Tiny bubble"))
    }

    /**
     * Two light panels behind the scene by colour temperature, warm on the left and cold on the right, in haze, and
     * a soft daylight panel overhead.
     */
    private fun lights(): List<Traceable> =
        panel(at(-3.6, 2.4, -4.6), Emissive.kelvin(2700.0, 2.0)) +
            panel(at(3.9, 2.6, -4.3), Emissive.kelvin(9000.0, 2.0)) +
            Meshes.panel(Pose(at(0.0, 7.0, 3.0), x = RIGHT, y = TOWARDS, z = UP * -1.0), 3.0, 2.0, KEY) +
            Fog(at(0.0, 1.8, -4.6), 4.2, 0.025, Color(0.9, 0.9, 0.9), "Haze")

    private fun panel(center: Point3, light: Emissive): List<Traceable> {
        val facing = Point3(-center.x, 0.0, -center.z).unit()
        val across = UP.cross(facing)
        return Meshes.panel(Pose(center, x = across, y = UP, z = facing), 1.0, 0.8, light)
    }

    /** Two balls caught in motion by the open shutter: one rolling across, one bouncing up. */
    private fun moving(): List<Traceable> = listOf(
        Sphere(at(1.9, 0.3, 4.4), 0.3, Plastic(Color(0.95, 0.1, 0.08), 0.03), "Rolling ball", velocity = RIGHT * 0.35),
        Sphere(at(-0.9, 0.3, 4.9), 0.3, Plastic(Color(0.2, 0.5, 1.0), 0.03), "Bouncing ball", velocity = UP * 0.35),
    )

    private val KEY = Emissive.kelvin(5500.0, 5.0)

    private val FLOOR = Patterned(
        Pattern(PatternKind.WOOD, Color(0.62, 0.4, 0.22), Color(0.26, 0.13, 0.05), scale = 2.0, turbulence = 0.35),
        roughness = 0.08,
        bump = 0.01,
    )
}
