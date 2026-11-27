package krt.domain.trace

import kotlin.math.PI
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.Fog
import krt.domain.scene.model.GlowingPattern
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Pattern
import krt.domain.scene.model.PatternKind
import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Sky
import krt.domain.scene.model.Sphere
import krt.domain.scene.model.Triangle
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

class LightSamplingTest {

    @Test
    fun `samples of a sphere light add up to its irradiance`() {
        val radius = 0.5
        val distance = 4.0
        val lights = Lights(listOf(Sphere(Point3(0.0, distance, 0.0), radius, Emissive(Color(1.0, 1.0, 1.0), 1.0))))
        val random = Random(3)
        val irradiance = (0 until SAMPLES).sumOf {
            lights.sample(Point3.ZERO, random)?.let { it.emitted.x * it.direction.y / it.pdf } ?: 0.0
        } / SAMPLES
        val expected = PI * (radius / distance) * (radius / distance)
        assertEquals(expected, irradiance, expected * 0.03)
    }

    @Test
    fun `samples of a triangle light see it from both sides`() {
        val corners = listOf(Point3(-1.0, 2.0, -1.0), Point3(1.0, 2.0, -1.0), Point3(0.0, 2.0, 1.0))
        val light = Triangle(corners[0], corners[1], corners[2], Emissive(WHITE, 1.0))
        val lights = Lights(listOf(light))
        assertTrue(lights.sample(Point3.ZERO, Random(1)) != null)
        assertTrue(lights.sample(Point3(0.0, 4.0, 0.0), Random(1)) != null)
    }

    @Test
    fun `only emissive spheres and triangles are lights`() {
        val lights = Lights(listOf(Sphere(Point3.ZERO, 1.0, Lambert(WHITE)), Fog(Point3.ZERO, 1.0, 1.0, WHITE)))
        assertEquals(0, lights.count)
        assertNull(lights.sample(Point3(5.0, 0.0, 0.0), Random(1)))
    }

    @Test
    fun `light sampling changes the noise of a frame but not its brightness`() {
        val objects = listOf(
            Sphere(Point3(0.0, -100.0, 0.0), 100.0, Lambert(Color(0.7, 0.7, 0.7))),
            Sphere(Point3(0.0, 3.0, 0.0), 0.4, Emissive(Color(1.0, 0.9, 0.7), 20.0)),
            Fog(Point3(0.0, 1.0, 0.0), 1.5, 0.4, Color(0.9, 0.9, 0.9)),
        )
        val plain = TraceScene(objects, NIGHT)
        val sampled = TraceScene(objects, NIGHT.copy(sampleLights = true))
        val plainMean = meanBrightness(plain, Random(7))
        val sampledMean = meanBrightness(sampled, Random(8))
        assertTrue(abs(plainMean - sampledMean) < plainMean * 0.05, "plain $plainMean vs sampled $sampledMean")
    }

    @Test
    fun `a glowing pattern is a light and sampling it keeps the brightness`() {
        val lava = GlowingPattern(Pattern(PatternKind.MARBLE, Color(1.0, 0.3, 0.0), Color(0.2, 0.0, 0.0), 2.0), 6.0)
        val objects = listOf(
            Sphere(Point3(0.0, -100.0, 0.0), 100.0, Lambert(Color(0.7, 0.7, 0.7))),
            Sphere(Point3(0.0, 2.5, 0.0), 0.6, lava),
        )
        assertEquals(1, Lights(objects).count)
        val plainMean = meanBrightness(TraceScene(objects, NIGHT), Random(7))
        val sampledMean = meanBrightness(TraceScene(objects, NIGHT.copy(sampleLights = true)), Random(8))
        assertTrue(abs(plainMean - sampledMean) < plainMean * 0.05, "plain $plainMean vs sampled $sampledMean")
    }

    @Test
    fun `fog lets through the share of rays its density allows`() {
        val density = 0.5
        val fog = Fog(Point3.ZERO, 1.0, density, WHITE)
        val passed = (0 until SAMPLES).count { i ->
            fog.hit(Ray(Point3(i * 1e-7, 0.0, -5.0), Point3(0.0, 0.0, 1.0)), 0.001, Double.POSITIVE_INFINITY) == null
        }
        assertEquals(kotlin.math.exp(-density * 2), passed.toDouble() / SAMPLES, 0.02)
    }

    @Test
    fun `fog scatters inside its sphere and only before what the ray hits`() {
        val fog = Fog(Point3.ZERO, 1.0, 50.0, WHITE)
        val ray = Ray(Point3(0.0, 0.0, -5.0), Point3(0.0, 0.0, 1.0))
        val hit = fog.hit(ray, 0.001, Double.POSITIVE_INFINITY)
        assertTrue(hit != null && hit.t in 4.0..6.0)
        assertNull(fog.hit(ray, 0.001, 4.0))
    }

    /** Mean brightness of rays from above, down at the floor, through the fog and past the light. */
    private fun meanBrightness(scene: TraceScene, random: Random): Double = (0 until RAYS).sumOf {
        val origin = Point3(random.nextDouble(-3.0, 3.0), 6.0, random.nextDouble(-3.0, 3.0))
        val color = PathTracing.color(scene, Ray(origin, Point3(0.0, -1.0, 0.0) - origin * 0.1), DEPTH, random)
        (color.x + color.y + color.z) / 3
    } / RAYS

    private companion object {
        val WHITE = Color(1.0, 1.0, 1.0)
        val NIGHT = SceneLook(Sky(Color(0.02, 0.02, 0.03), Color(0.0, 0.0, 0.01)), fov = 60.0, aperture = 0.0)
        const val SAMPLES = 20_000
        const val RAYS = 40_000
        const val DEPTH = 6
    }
}
