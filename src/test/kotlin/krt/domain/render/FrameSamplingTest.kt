package krt.domain.render

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import krt.domain.camera.Camera
import krt.domain.scene.World
import krt.domain.scene.model.Dielectric
import krt.domain.scene.model.Triangle
import krt.platform.math.Point3

class FrameSamplingTest {

    @Test
    fun `pixel order covers every pixel exactly once`() {
        val pixels = FrameSampling.pixelOrder(7, 5, Random(1))
        assertEquals(35, pixels.size)
        assertEquals(35, pixels.toSet().size)
        assertEquals(pixels, FrameSampling.pixelOrder(7, 5, Random(1)))
    }

    @Test
    fun `image row is flipped`() {
        assertEquals(9, FrameSampling.imageY(FrameSampling.Pixel(0, 0), 10))
        assertEquals(0, FrameSampling.imageY(FrameSampling.Pixel(0, 9), 10))
    }

    @Test
    fun `repaint only after the interval has passed`() {
        assertFalse(FrameSampling.shouldRepaint(now = 150, lastRepaint = 100, repaintIntervalMs = 50))
        assertTrue(FrameSampling.shouldRepaint(now = 151, lastRepaint = 100, repaintIntervalMs = 50))
    }

    @Test
    fun `wireframe drops segments behind the camera`() {
        val camera = Camera(Point3.ZERO, Point3(1.0, 0.0, 0.0), Point3(0.0, 1.0, 0.0), 90.0, 0.0, 1.0, 100, 100)
        val inFront = Triangle(Point3(5.0, 0.0, 0.0), Point3(5.0, 1.0, 0.0), Point3(5.0, 0.0, 1.0), Dielectric(1.5))
        val behind = Triangle(Point3(-5.0, 0.0, 0.0), Point3(-5.0, 1.0, 0.0), Point3(-5.0, 0.0, 1.0), Dielectric(1.5))
        assertEquals(3, FrameSampling.wireframe(listOf(inFront, behind), camera).size)
    }

    @Test
    fun `pixel sample is deterministic for a given random`() {
        val camera = Camera.default()
        val tracer = RayTracer(World.default(Random(5)), 3)
        val pixel = FrameSampling.Pixel(10, 20)
        assertEquals(
            FrameSampling.samplePixel(pixel, camera, tracer, Random(9)),
            FrameSampling.samplePixel(pixel, camera, tracer, Random(9)),
        )
    }
}
