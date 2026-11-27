package krt.domain.scene.model

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import krt.platform.math.Color
import krt.platform.math.Point3

/**
 * A scene to show off the tracer at dusk: an amber glass torus knot on a polished checker floor, a mirror ball,
 * glossy plastic, a blue glass gem and a gold ring, lit by the sky and two coloured light panels, with small
 * spheres around. It is laid out for the overview view, from (13, 2, 3) towards the centre.
 */
@Suppress("MagicNumber") // positions, sizes and colours are the scene
object Showcase {

    private val DUSK = Sky(horizon = Color(1.0, 0.72, 0.5), zenith = Color(0.12, 0.2, 0.45))

    val LOOK = SceneLook(DUSK, fov = 34.0, aperture = 0.05)

    /** Across the view of the overview, to the right on screen. */
    private val RIGHT = Point3(0.222, 0.0, -0.963).unit()

    /** Along the view of the overview, towards the camera. */
    private val TOWARDS = Point3(0.974, 0.0, 0.225).unit()
    private val UP = Point3(0.0, 1.0, 0.0)

    private fun at(right: Double, up: Double, towards: Double) = RIGHT * right + UP * up + TOWARDS * towards

    fun objects(random: Random): List<Traceable> = buildList {
        addAll(heroes())
        addAll(lights())
        addAll(scattered(random))
    }

    private fun heroes(): List<Traceable> = buildList {
        add(Sphere(Point3(0.0, -1000.0, 0.0), 1000.0, FLOOR, "Floor"))
        add(Sphere(at(-2.9, 1.0, 0.0), 1.0, Metal(Color(0.92, 0.92, 0.95), 0.0), "Mirror ball"))
        add(Sphere(at(2.6, 0.7, 0.8), 0.7, Plastic(Color(0.85, 0.08, 0.06), 0.02), "Red plastic"))
        add(Sphere(at(0.9, 0.35, 2.4), 0.35, Plastic(Color(0.9, 0.9, 0.9), 0.05), "White plastic"))
        addAll(knot())
        addAll(ring())
        addAll(gem())
    }

    private fun knot() = Meshes.torusKnot(
        Pose(Point3(0.0, 1.38, 0.0), x = RIGHT, y = TOWARDS, z = UP * -1.0),
        scale = 0.36,
        tube = 0.26,
        p = 2,
        q = 3,
        segments = 200,
        sides = 14,
        material = Dielectric(1.5, tint = Color(1.0, 0.7, 0.3), density = 0.6),
    )

    private fun ring() = Meshes.torus(
        Pose(at(1.6, 1.05, -2.6), x = RIGHT, y = TOWARDS, z = UP),
        radius = 0.8,
        tube = 0.2,
        segments = 64,
        sides = 16,
        material = Metal(Color(1.0, 0.76, 0.33), 0.12),
    )

    private fun gem() = Meshes.icosphere(
        Pose(at(-1.2, 0.5, 2.6)),
        radius = 0.5,
        subdivisions = 1,
        material = Dielectric(1.5, tint = Color(0.25, 0.45, 1.0), density = 1.5),
    )

    /** Two light panels behind the scene, turned towards its centre: warm on the left, cool on the right. */
    private fun lights(): List<Traceable> =
        panel(at(-3.5, 2.4, -4.5), Emissive(Color(1.0, 0.55, 0.25), 1.6)) +
            panel(at(3.8, 2.6, -4.2), Emissive(Color(0.3, 0.7, 1.0), 1.6))

    private fun panel(center: Point3, light: Emissive): List<Traceable> {
        val facing = Point3(-center.x, 0.0, -center.z).unit()
        val across = UP.cross(facing)
        return Meshes.panel(Pose(center, x = across, y = UP, z = facing), 1.0, 0.8, light)
    }

    /** Small spheres on a ring around the centre, clear of the large objects. */
    private fun scattered(random: Random): List<Traceable> {
        val blockers =
            listOf(Point3.ZERO, at(-2.9, 0.0, 0.0), at(2.6, 0.0, 0.8), at(1.6, 0.0, -2.6), at(-1.2, 0.0, 2.6))
        return List(SCATTERED) {
            val angle = random.nextDouble(0.0, 2 * Math.PI)
            val distance = random.nextDouble(3.4, 6.5)
            val radius = random.nextDouble(0.15, 0.3)
            Sphere(Point3(distance * cos(angle), radius, distance * sin(angle)), radius, smallMaterial(random))
        }.filter { sphere -> blockers.none { (Point3(sphere.center.x, 0.0, sphere.center.z) - it).length() < 1.5 } }
    }

    private fun smallMaterial(random: Random): Material {
        val color = VIVID[random.nextInt(VIVID.size)]
        return when (random.nextInt(4)) {
            0 -> Metal(color, random.nextDouble(0.0, 0.2))
            1 -> Dielectric(1.5, tint = color, density = 2.0)
            else -> Plastic(color, random.nextDouble(0.0, 0.1))
        }
    }

    private const val SCATTERED = 40

    private val FLOOR = Checker(Color(0.82, 0.82, 0.85), Color(0.06, 0.06, 0.07), cellSize = 1.0, roughness = 0.06)

    private val VIVID = listOf(
        Color(0.95, 0.3, 0.35), Color(1.0, 0.75, 0.2), Color(0.3, 0.8, 0.5),
        Color(0.25, 0.55, 1.0), Color(0.7, 0.4, 1.0), Color(0.95, 0.95, 0.95),
    )
}
