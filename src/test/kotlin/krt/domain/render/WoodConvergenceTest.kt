package krt.domain.render

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.atan
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import krt.domain.camera.Camera
import krt.domain.render.model.RenderMode
import krt.domain.render.model.RenderProgress
import krt.domain.scene.World
import krt.domain.scene.model.Pattern
import krt.domain.scene.model.PatternKind
import krt.domain.scene.model.Patterned
import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Sky
import krt.domain.scene.model.Triangle
import krt.platform.math.Point3
import krt.platform.skia.MetalContext
import org.junit.jupiter.api.Assumptions.assumeTrue

/** One pixel spans 1/8 of a ring, centred on its dark ridge. Integrating twice visibly flattens the ridge. */
class WoodConvergenceTest {
    private val eye = Point3(0.0, 0.9 / FREQUENCY, 1.0)
    private val camera = Camera(eye, eye + Point3(0, 0, -1), Point3(0, 1, 0),
        Math.toDegrees(2 * atan(WIDTH / 2)), 0.0, 1.0, COLUMNS, 1).apply { filterWood = true }
    private val wood = Pattern(PatternKind.WOOD, Point3.ZERO, Point3(1, 0, 0), turbulence = 0.0, frequency = FREQUENCY)
    private val floor = Triangle(Point3(-2, -2, 0), Point3(2, -2, 0), Point3(0, 2, 0), Patterned(wood, 0.0))
    private val world = World(listOf(floor), SceneLook(Sky(Point3.ONE, Point3.ONE), camera.fov, 0.0))
    private val tracer = RayTracer(world, 2)

    @Test
    fun `CPU and SkSL keep the integrated close wood ridge with independent and stratified samples`() {
        val reference = rawIntegral()
        for (stratified in listOf(false, true)) {
            camera.stratifiedPixels = stratified
            camera.samplesPerPixel = 256
            val cpu = (0 until COLUMNS).map { x ->
                val sum = FrameSampling.samplePixel(FrameSampling.Pixel(x, 0), camera, tracer, Random(x + 73))
                diffuse(sum.x / camera.samplesPerPixel, sum.y / camera.samplesPerPixel)
            }.average()
            checkRidge(reference, cpu)
            val band = GpuBand(targetSamples = 64)
            fun step(samples: Int, previous: Int) = GpuFrame(world.gpuScene, camera.view(), COLUMNS, 1, 2, samples,
                0.37f, band.copy(previousSamples = previous))
            checkRidge(reference, meanDiffuse(rasterPixels(step(64, 0))))
            checkRidge(reference, meanDiffuse(rasterPixels(step(3, 0), step(61, 3))))
        }
    }

    @Test
    fun `progressive CPU batches keep the full frame sampling density`() {
        RenderController(10L, 4, Random(17)).use { controller ->
            for (stratified in listOf(false, true)) {
                camera.stratifiedPixels = stratified
                checkProgressiveFrame(controller)
            }
        }
    }

    @Test
    fun `offscreen Metal keeps the full frame sampling density over many small steps`() {
        val context = MetalContext.create()
        assumeTrue(context != null, "no Metal here")
        camera.stratifiedPixels = true
        OffscreenGpu(requireNotNull(context)).use { gpu ->
            RenderController(10L, 4, Random(17), offscreen = gpu).use { controller ->
                controller.mode = RenderMode.GPU
                checkProgressiveFrame(controller)
            }
        }
    }

    private fun checkProgressiveFrame(controller: RenderController) {
        camera.samplesPerPixel = 256
        val frame = Frame(COLUMNS, 1)
        val done = CompletableFuture<RenderProgress>()
        controller.renderImage(frame, tracer, camera) { if (it.isDone || it.failure != null) done.complete(it) }
        val progress = done.get(8, TimeUnit.SECONDS)
        assertNull(progress.failure)
        assertEquals(camera.samplesPerPixel, progress.completedPasses)
        checkRidge(rawIntegral(), meanDiffuse(frame.pixels))
    }

    private fun rawIntegral(): Double {
        var sum = 0.0
        for (j in 0 until GRID) for (i in 0 until GRID) {
            val x = ((i + 0.5) / GRID - 0.5) * WIDTH * COLUMNS
            val y = eye.y + ((j + 0.5) / GRID - 0.5) * WIDTH
            sum += wood.valueAt(Point3(x, y, 0.0))
        }
        return sum / (GRID * GRID)
    }

    private fun checkRidge(expected: Double, actual: Double) {
        assertTrue(abs(actual - expected) < 0.015, "ridge: $actual, unfiltered box integral: $expected")
    }

    private fun meanDiffuse(pixels: IntArray): Double = pixels.map {
        val red = (it ushr 16 and 255) / 255.0
        val green = (it ushr 8 and 255) / 255.0
        diffuse(red * red, green * green)
    }.average()

    // A white sky contributes equally to both channels for the clear coat. The diffuse layer contributes only red.
    private fun diffuse(red: Double, green: Double): Double = (red - green) / (1 - green)

    private companion object {
        const val COLUMNS = 16
        const val FREQUENCY = 1024.0
        const val WIDTH = 0.125 / FREQUENCY
        const val GRID = 64
    }
}
