package krt.scenes

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import krt.domain.scene.model.DemoScene
import krt.domain.scene.model.Dielectric
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Meshes
import krt.domain.scene.model.Pose
import krt.domain.scene.model.Sky
import krt.domain.scene.model.Traceable
import krt.domain.scene.model.Triangle
import krt.domain.trace.PathTracing
import krt.domain.trace.TraceScene
import krt.i9ns.scenefile.SceneLibrary
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

/** The camera sees a white diffuse receiver lit through a prism. The gallery camera looks through the prism. */
class ProjectedCausticTest {
    private val demo = SceneLibrary().load(DemoScene.PRISM)
    private val glass = demo.objects.filterIsInstance<Triangle>().filter { it.id == "Prism" }
    private val screenMaterial = Lambert(Color.ONE)
    private val axis = Point3(cos(50 * PI / 180), 0.0, sin(50 * PI / 180))
    private val across = Point3(-axis.z, 0.0, axis.x)
    private val centre = Point3(0.0, 1.0, 0.0) + axis * 1.75

    private fun scene(prism: List<Triangle>): TraceScene {
        val screen = Meshes.panel(Pose(centre, across, Point3(0, 1, 0), -axis), 1.15, 0.8, screenMaterial)
        // The narrower neutral panel behind the slit separates the colours across the receiver.
        val lamp = Meshes.panel(
            Pose(Point3(-2.3, 1.0, 0.0), Point3(0, 0, -1), Point3(0, 1, 0), Point3(1, 0, 0)),
            0.05, 0.5, Emissive(Color.ONE, 5.0),
        )
        val house: List<Traceable> = demo.objects.filter { it.id == "Lamp house" }
        return TraceScene(house + lamp + prism + screen, demo.look.copy(sky = Sky(Color.ZERO, Color.ZERO)))
    }

    @Test
    fun `a matte screen carries a red and a blue side after light passes through the prism`() {
        val lit = scene(glass)
        val red = measure(lit, -2)
        val blue = measure(lit, 2)
        assertTrue(red.x > 0.002 && red.x > 1.5 * max(red.y, red.z), "red side of the screen: $red")
        assertTrue(blue.z > 0.002 && blue.z > 2 * max(blue.x, blue.y), "blue side of the screen: $blue")
    }

    @Test
    fun `constant index glass projects neutral light and removing the prism removes the deflected beam`() {
        val clear = glass.map { Triangle(it.v0, it.v1, it.v2, Dielectric(1.62), it.id) }
        val neutral = measure(scene(clear), -2)
        assertTrue(neutral.x > 0.002, "constant-index glass illuminates the receiver: $neutral")
        assertEquals(neutral.x, neutral.y, 1e-6)
        assertEquals(neutral.x, neutral.z, 1e-6)
        val noGlass = measure(scene(emptyList()), 2)
        assertTrue(maxOf(noGlass.x, noGlass.y, noGlass.z) < 1e-5, "off-axis receiver without glass: $noGlass")
    }

    @Test
    fun `three interactions are too few for receiver entry exit and emitter`() {
        val shortPath = measure(scene(glass), 2, depth = 3)
        assertTrue(maxOf(shortPath.x, shortPath.y, shortPath.z) < 1e-5, "short paths: $shortPath")
    }

    /** Mean linear RGB over a small strip of screen. All camera rays start 0.05 units in front of the receiver. */
    private fun measure(scene: TraceScene, bin: Int, depth: Int = 4): Color {
        val random = Random(71 + bin)
        val point = centre + across * (bin * 0.075)
        val centreRay = Ray(point - axis * 0.05, axis)
        assertSame(screenMaterial, scene.hit(centreRay, 1e-4, 1.0)?.material)
        var sum = Color.ZERO
        repeat(SAMPLES) {
            val x = 0.055 * (random.nextDouble() - 0.5)
            val y = 0.1 * (random.nextDouble() - 0.5)
            val origin = centreRay.origin + across * x + Point3(0.0, y, 0.0)
            sum += PathTracing.color(scene, Ray(origin, axis), depth, random)
        }
        return sum / SAMPLES.toDouble()
    }

    private companion object {
        const val SAMPLES = 160_000
    }
}
