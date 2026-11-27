package krt.domain.trace

import kotlin.math.PI
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.Fog
import krt.domain.scene.model.GlowingPattern
import krt.domain.scene.model.Interface
import krt.domain.scene.model.Iridescent
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Medium
import krt.domain.scene.model.Meshes
import krt.domain.scene.model.Pose
import krt.domain.scene.model.Pattern
import krt.domain.scene.model.PatternKind
import krt.domain.scene.model.Patterned
import krt.domain.scene.model.Plastic
import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Sky
import krt.domain.scene.model.Sphere
import krt.domain.scene.model.Triangle
import krt.domain.scene.model.Velvet
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
            lights.sample(Point3.ZERO, 0.0, random)?.let { it.emitted.x * it.direction.y / it.pdf } ?: 0.0
        } / SAMPLES
        val expected = PI * (radius / distance) * (radius / distance)
        assertEquals(expected, irradiance, expected * 0.03)
    }

    @Test
    fun `samples of a triangle light see it from both sides`() {
        val corners = listOf(Point3(-1.0, 2.0, -1.0), Point3(1.0, 2.0, -1.0), Point3(0.0, 2.0, 1.0))
        val light = Triangle(corners[0], corners[1], corners[2], Emissive(WHITE, 1.0))
        val lights = Lights(listOf(light))
        assertTrue(lights.sample(Point3.ZERO, 0.0, Random(1)) != null)
        assertTrue(lights.sample(Point3(0.0, 4.0, 0.0), 0.0, Random(1)) != null)
    }

    @Test
    fun `the triangles of one object are one light, picked by area times brightness`() {
        val letter = Meshes.panel(Pose(Point3(0.0, 2.0, 0.0)), 0.1, 0.1, Emissive(Color.ONE, 10.0))
            .map { Triangle(it.v0, it.v1, it.v2, it.material, "letter_D") }
        val lamp = Sphere(Point3(3.0, 2.0, 0.0), 0.5, Emissive(Color.ONE, 1.0))
        val emitters = Emitters.of(letter + lamp)
        assertEquals(listOf(2, 1), emitters.map { it.members.size })
        assertEquals(0.04 * 10.0, emitters[0].weight, 1e-9)
        assertEquals(PI, emitters[1].weight, 1e-9)
        assertEquals(2, Lights(letter + lamp).count)
    }

    @Test
    fun `the log code of a positive number keeps it to a hundred-thousandth`() {
        listOf(1e-6, 0.004, 1.0, 37.5, 1e9).forEach { value ->
            assertEquals(value, GpuLightTable.decodeLog(GpuLightTable.encodeLog(value)), value * 1e-5)
        }
    }

    @Test
    fun `only emissive spheres and triangles are lights`() {
        val lights = Lights(listOf(Sphere(Point3.ZERO, 1.0, Lambert(WHITE)), Fog(Point3.ZERO, 1.0, 1.0, WHITE)))
        assertEquals(0, lights.count)
        assertNull(lights.sample(Point3(5.0, 0.0, 0.0), 0.0, Random(1)))
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
    fun `light sampling keeps the brightness of a lit floor of every material it lights`() {
        val floors = listOf(
            Lambert(Color(0.7, 0.7, 0.7)),
            Plastic(Color(0.7, 0.3, 0.2), 0.2),
            Velvet(Color(0.4, 0.1, 0.2), Color(0.8, 0.6, 0.6), 0.5),
            Iridescent(Color(0.5, 0.5, 0.5), 450.0, 1.8, 0.2),
            Patterned(Pattern(PatternKind.MARBLE, Color(0.8, 0.8, 0.8), Color(0.2, 0.2, 0.3), 2.0), 0.2, bump = 0.3),
        )
        floors.forEach { floor ->
            val objects = listOf(
                Sphere(Point3(0.0, -100.0, 0.0), 100.0, floor),
                Sphere(Point3(0.0, 1.5, 0.0), 0.6, Emissive(Color.ONE, 4.0)),
            )
            val plain = floorBrightness(TraceScene(objects, NIGHT), Random(11))
            val sampled = floorBrightness(TraceScene(objects, NIGHT.copy(sampleLights = true)), Random(12))
            val name = floor::class.simpleName
            assertTrue(abs(plain - sampled) < plain * 0.03, "$name: plain $plain vs sampled $sampled")
        }
    }

    /** Mean brightness of the floor around the light, seen straight from above, away from the light itself. */
    private fun floorBrightness(scene: TraceScene, random: Random): Double = (0 until FLOOR_RAYS).sumOf {
        val angle = random.nextDouble(0.0, 2 * PI)
        val distance = random.nextDouble(0.9, 2.5)
        val origin = Point3(distance * kotlin.math.cos(angle), 4.0, distance * kotlin.math.sin(angle))
        val color = PathTracing.color(scene, Ray(origin, Point3(0.0, -1.0, 0.0)), DEPTH, random)
        (color.x + color.y + color.z) / 3
    } / FLOOR_RAYS

    @Test
    fun `fog lets through the share of light its density allows, per channel`() {
        val density = Color(0.2, 0.5, 1.0)
        val fog = Fog(Point3.ZERO, 1.0, Medium(density, Color.ZERO))
        val scene = TraceScene(listOf(fog), SceneLook(Sky(WHITE, WHITE), fov = 60.0, aperture = 0.0))
        val random = Random(9)
        val mean = (0 until SAMPLES).fold(Point3.ZERO) { sum, i ->
            sum + PathTracing.color(scene, Ray(Point3(i * 1e-7, 0.0, -5.0), Point3(0.0, 0.0, 1.0)), DEPTH, random)
        } / SAMPLES.toDouble()
        assertEquals(kotlin.math.exp(-0.4), mean.x, 0.015)
        assertEquals(kotlin.math.exp(-1.0), mean.y, 0.015)
        assertEquals(kotlin.math.exp(-2.0), mean.z, 0.015)
    }

    @Test
    fun `the edge of fog is an interface, and a camera inside fog starts in its medium`() {
        val fog = Fog(Point3.ZERO, 2.0, 0.3, WHITE)
        val hit = assertNotNull(fog.hit(Ray(Point3(0.0, 0.0, -5.0), Point3(0.0, 0.0, 1.0)), 0.001, 100.0))
        assertEquals(Interface, hit.material)
        assertEquals(fog.medium, hit.media?.inside)
        val scene = TraceScene(listOf(fog))
        assertEquals(fog.medium, scene.mediumAt(Point3(0.5, 0.0, 0.0)))
        assertNull(scene.mediumAt(Point3(5.0, 0.0, 0.0)))
    }

    @Test
    fun `light sampling through fog keeps the brightness of the floor beneath it`() {
        val objects = listOf(
            Sphere(Point3(0.0, -100.0, 0.0), 100.0, Lambert(Color(0.7, 0.7, 0.7))),
            Sphere(Point3(0.0, 3.0, 0.0), 0.5, Emissive(Color.ONE, 10.0)),
            Fog(Point3(0.0, 1.0, 0.0), 2.5, Medium(Color(0.1, 0.25, 0.4), Color(0.9, 0.9, 0.9))),
        )
        val plain = floorBrightness(TraceScene(objects, NIGHT), Random(21))
        val sampled = floorBrightness(TraceScene(objects, NIGHT.copy(sampleLights = true)), Random(22))
        assertTrue(abs(plain - sampled) < plain * 0.04, "plain $plain vs sampled $sampled")
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
        const val FLOOR_RAYS = 60_000
    }
}
