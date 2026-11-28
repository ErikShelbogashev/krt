package krt.domain.render

import kotlin.math.PI
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import krt.domain.camera.CameraView
import krt.domain.scene.World
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.GlowingPattern
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Material
import krt.domain.scene.model.Pattern
import krt.domain.scene.model.PatternKind
import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Sky
import krt.domain.scene.model.Triangle
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

class HdrAreaLightTest {
    @Test
    fun `doubling the power of a small HDR area light doubles received radiance`() {
        for (power in listOf(4096.0, 8192.0, 16384.0)) {
            checkReceiver(Emissive(Color.ONE, power), Color.ONE * power)
        }
    }

    @Test
    fun `bright emissive and patterned sources retain their per-channel ratios`() {
        val tint = Color(1.0, 0.375, 0.0)
        val power = 32768.0
        val striped = Pattern(PatternKind.STRIPES, tint, tint)
        checkReceiver(Emissive(tint, power), tint * power)
        checkReceiver(GlowingPattern(striped, power), tint * power)
    }

    @Test
    fun `a visible HDR lamp keeps weak channels next to a bright channel`() {
        val lamp = Emissive(Color(1e31, 0.25, 0.0), 1.0)
        val panel = Triangle(Point3(-2, -2, 0), Point3(2, -2, 0), Point3(0, 2, 0), lamp)
        val view = CameraView(Point3(0, 0, 1), Point3.ZERO, Point3.ZERO, Point3.ZERO, Point3.ZERO, Point3.ZERO, 0.0)
        val scene = World(listOf(panel))
        val rgb = rasterPixels(GpuFrame(scene.gpuScene, view, 2, 2, 1, 1, 0.5f)).first()
        assertEquals(255, rgb shr 16 and 255)
        assertEquals(128.0, (rgb shr 8 and 255).toDouble(), 1.0)
        assertEquals(0, rgb and 255)
    }

    private fun checkReceiver(lamp: Material, emission: Color) {
        val floor = Triangle(Point3(-2, -2, 0), Point3(2, -2, 0), Point3(0, 2, 0), Lambert(Color.ONE))
        val z = 32.0
        val source = Triangle(Point3(-0.125, -0.125, z), Point3(0.125, -0.125, z), Point3(0.0, 0.125, z), lamp)
        val scene = World(listOf(floor, source), SceneLook.CLASSIC.copy(sky = Sky(Color.ZERO, Color.ZERO),
            sampleLights = true))
        val eye = Point3(0, 0, 1)
        val ray = Ray(eye, -eye)
        val cpu = RayTracer(scene, 2).color(ray, Random(42))
        val view = CameraView(eye, Point3.ZERO, Point3.ZERO, Point3.ZERO, Point3.ZERO, Point3.ZERO, 0.0)
        val raster = rasterPixels(GpuFrame(scene.gpuScene, view, 2, 2, 2, 8, 0.5f))
        // For an on-axis small triangle, cos(theta)^2/r² differs from 1/z² by less than 0.007%.
        val area = 0.25 * 0.25 / 2
        for (channel in 0..2) {
            val expected = emission[channel] * area / (PI * z * z)
            val code = (raster.first() shr ((2 - channel) * 8) and 255) / 255.0
            assertEquals(expected, cpu[channel], expected * 0.0001)
            assertEquals(expected, code * code, 0.004, "SkSL channel $channel, emitted ${emission[channel]}")
        }
    }
}
