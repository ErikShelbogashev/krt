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
    fun `the pixel order in place matches the list of pixels`() {
        val order = IntArray(35)
        FrameSampling.fillPixelOrder(order, Random(1))
        assertEquals(FrameSampling.pixelOrder(7, 5, Random(1)), order.map { FrameSampling.Pixel(it % 7, it / 7) })
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

    @Test
    fun `GPU steps double the samples taken so far, up to the step limit`() {
        assertEquals(listOf(1), GpuSampling.steps(1))
        assertEquals(listOf(1, 1, 2, 1), GpuSampling.steps(5))
        val fifty = GpuSampling.steps(50, maxStep = 4)
        assertEquals(listOf(1, 1, 2, 4, 4), fifty.take(5))
        assertEquals(50, fifty.sum())
        assertEquals(4, fifty.max())
    }

    @Test
    fun `slow GPU steps take fewer rows, then fewer samples, and grow back when they fit`() {
        val full = GpuSampling.StepBudget()
        assertEquals(full.copy(slow = true), full.after(70.0), "one step a little slow is a hiccup")
        assertEquals(GpuSampling.StepBudget(4, 2), full.after(70.0).after(80.0))
        assertEquals(GpuSampling.StepBudget(4, 10), full.after(400.0), "a step far too slow cuts at once")
        assertEquals(GpuSampling.StepBudget(2, 64), GpuSampling.StepBudget(4, 64).after(80.0), "samples go last")
        assertEquals(GpuSampling.StepBudget(1, 5), GpuSampling.StepBudget(1, 1, slow = true).after(180.0))
        assertEquals(full, full.after(70.0).after(10.0))
        var budget = GpuSampling.StepBudget(1, 5)
        repeat(3) { budget = budget.after(10.0) }
        assertEquals(5, budget.bands)
        assertEquals(GpuSampling.StepBudget(1, 3), budget.after(10.0))
        assertEquals(GpuSampling.StepBudget(2, 1), GpuSampling.StepBudget(1, 1, fitStreak = 3).after(15.0))
        assertEquals(GpuSampling.StepBudget(1, 1, 0), GpuSampling.StepBudget(1, 1, fitStreak = 3).after(30.0))
        assertEquals(listOf(1, 1, 2, 3), listOf(0, 1, 2, 4).map { GpuSampling.nextStep(it, 7, 3) })
    }

    @Test
    fun `bands of rows cover the frame once, top to bottom`() {
        val bands = (0 until 7).map { GpuSampling.bandRows(100, it, 7) }
        assertEquals((0 until 100).toList(), bands.flatten())
        assertEquals(listOf(2, 2, 5), (0 until 3).map { GpuSampling.samplesDone(2, 3, it, 3) })
    }

    @Test
    fun `the quick look of a GPU frame keeps within its pixel budget`() {
        assertEquals(1, GpuSampling.draftScale(400, 400))
        assertEquals(3, GpuSampling.draftScale(1024, 1024))
        assertEquals(2, GpuSampling.draftScale(800, 600))
        assertEquals(1, GpuSampling.draftScale(32, 32))
    }
}
