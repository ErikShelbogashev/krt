package krt.domain.render

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import krt.domain.camera.Camera
import krt.domain.render.model.RenderMode
import krt.domain.render.model.RenderProgress
import krt.domain.scene.World
import krt.domain.scene.model.DemoScene
import krt.i9ns.scenefile.SceneLibrary
import krt.platform.math.Point3
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface

class RenderControllerTest {

    @Test
    fun `every frame update of a ray traced render arrives on the UI thread`() {
        val camera = Camera.default().apply { imageWidth = 16; imageHeight = 16; samplesPerPixel = 1 }
        val frame = Frame(16, 16)
        val pixels = 16 * 16
        val batch = 8
        val onUiThread = Collections.synchronizedList(mutableListOf<Boolean>())
        val done = CountDownLatch(1)
        var lastTick = 0L

        // A clock that advances on every read makes every batch publish an intermediate update.
        RenderController(0L, batch, Random(1), clock = { ++lastTick }).use { controller ->
            controller.renderImage(frame, RayTracer(World.default(Random(2)), 3), camera) {
                onUiThread += SwingUtilities.isEventDispatchThread()
                if (onUiThread.size == pixels / batch + 2) done.countDown() // the quick look, batches, the end
            }
            assertTrue(done.await(30, TimeUnit.SECONDS), "render did not finish")
        }

        assertEquals(List(pixels / batch + 2) { true }, onUiThread.toList())
    }

    @Test
    fun `a progressive frame publishes after every batch of every pass`() {
        val passes = 3
        val camera = Camera.default().apply { imageWidth = 16; imageHeight = 16; samplesPerPixel = passes }
        val frame = Frame(16, 16)
        val batch = 32
        val expected = passes * (16 * 16 / batch) + 2 // the quick look, batches, the end
        val updates = Collections.synchronizedList(mutableListOf<Unit>())
        val done = CountDownLatch(1)
        var lastTick = 0L

        RenderController(0L, batch, Random(1), clock = { ++lastTick }).use { controller ->
            controller.renderImage(frame, RayTracer(World.default(Random(2)), 3), camera) {
                updates += Unit
                if (updates.size == expected) done.countDown()
            }
            assertTrue(done.await(30, TimeUnit.SECONDS), "render did not finish")
        }

        assertEquals(expected, updates.size)
    }

    @Test
    fun `progress grows pass by pass and the last update is the finished frame`() {
        val passes = 3
        val camera = Camera.default().apply { imageWidth = 16; imageHeight = 16; samplesPerPixel = passes }
        val batch = 64
        val expected = passes * (16 * 16 / batch) + 2 // the quick look, batches, the end
        val progress = Collections.synchronizedList(mutableListOf<RenderProgress>())
        val done = CountDownLatch(1)
        var lastTick = 0L

        RenderController(0L, batch, Random(1), clock = { ++lastTick }).use { controller ->
            controller.renderImage(Frame(16, 16), RayTracer(World.default(Random(2)), 3), camera) {
                progress += it
                if (progress.size == expected) done.countDown()
            }
            assertTrue(done.await(30, TimeUnit.SECONDS), "render did not finish")
        }

        val updates = progress.toList()
        assertTrue(updates.dropLast(1).none { it.isDone })
        assertEquals((0 until passes).toSet(), updates.dropLast(1).map { it.completedPasses }.toSet())
        assertEquals(RenderProgress.finished(passes, updates.last().elapsedMs), updates.last())
    }

    @Test
    fun `a CPU frame starts with a quick look that covers the whole frame`() {
        val camera = Camera.default().apply { imageWidth = 64; imageHeight = 48; samplesPerPixel = 2 }
        val frame = Frame(64, 48)
        val quickLook = CountDownLatch(1)
        var covered = 0
        RenderController(Long.MAX_VALUE, 64, Random(1)).use { controller ->
            controller.renderImage(frame, RayTracer(World.default(Random(2)), 3), camera) { progress ->
                if (progress.completedPasses == 0 && progress.passFraction == 0.0) {
                    covered = frame.pixels.count { it != 0 }
                    quickLook.countDown()
                }
            }
            assertTrue(quickLook.await(30, TimeUnit.SECONDS), "no quick look")
        }
        assertEquals(64 * 48, covered)
    }

    @Test
    fun `GPU steps follow one another as soon as the window has drawn them`() {
        val camera = Camera.default().apply { imageWidth = 16; imageHeight = 16; samplesPerPixel = 5 }
        val done = CountDownLatch(1)
        val steps = AtomicInteger()
        // A repaint interval of a minute: without the window's word, the second step would come a minute later.
        RenderController(60_000L, 8, Random(1)).use { controller ->
            controller.mode = RenderMode.GPU
            val rayTracer = RayTracer(World.default(Random(2)), 3)
            controller.renderImage(Frame(16, 16), rayTracer, camera, onGpuFrame = { shader, progress ->
                steps.incrementAndGet()
                controller.gpuFrameShown(shader, 5.0)
                if (progress.isDone) done.countDown()
            }) {}
            assertTrue(done.await(10, TimeUnit.SECONDS), "steps waited for the repaint interval")
        }
        // Quick steps grow out of the cautious start: fewer than the 1 + 16 × 4 steps of a cautious frame.
        assertTrue(steps.get() < 40, "${steps.get()} steps")
    }

    @Test
    fun `slow GPU quick looks shrink the next ones, other frames change nothing`() {
        RenderController(0L, 8, Random(1)).use { controller ->
            val quickLook = CountDownLatch(1)
            var draft: org.jetbrains.skia.Shader? = null
            controller.mode = RenderMode.GPU
            val camera = Camera.default().apply { imageWidth = 400; imageHeight = 400; samplesPerPixel = 1 }
            val rayTracer = RayTracer(World.default(Random(2)), 3)
            controller.renderImage(Frame(400, 400), rayTracer, camera, onGpuFrame = { shader, p ->
                if (p.completedPasses == 0) {
                    draft = shader
                    quickLook.countDown()
                }
            }) {}
            assertTrue(quickLook.await(30, TimeUnit.SECONDS))
            val before = controller.draftPixels
            controller.gpuFrameShown(org.jetbrains.skia.Shader.makeEmpty(), 50.0)
            assertEquals(before, controller.draftPixels)
            controller.gpuFrameShown(requireNotNull(draft), 50.0)
            assertTrue(controller.draftPixels < before)
        }
    }

    @Test
    fun `in GPU mode the frame comes in steps on the UI thread and the pixel buffer is left alone`() {
        val camera = Camera.default().apply { imageWidth = 16; imageHeight = 16; samplesPerPixel = 5 }
        val frame = Frame(16, 16)
        val steps = Collections.synchronizedList(mutableListOf<RenderProgress>())
        val onUiThread = Collections.synchronizedList(mutableListOf<Boolean>())
        val done = CountDownLatch(1)
        var pixelUpdates = 0

        RenderController(0L, 8, Random(1)).use { controller ->
            controller.mode = RenderMode.GPU
            val rayTracer = RayTracer(World.default(Random(2)), 3)
            controller.renderImage(frame, rayTracer, camera, onGpuFrame = { _, progress ->
                steps += progress
                onUiThread += SwingUtilities.isEventDispatchThread()
                if (progress.isDone) done.countDown()
            }) { pixelUpdates++ }
            assertTrue(done.await(30, TimeUnit.SECONDS), "GPU frame did not finish")
        }

        // A quick look first; a new scene then starts cautious, a band of rows per step: 16 bands of each pass.
        assertEquals(listOf(0, 1, 2, 4, 5), steps.map { it.completedPasses }.distinct())
        assertEquals(1 + 16 * 4, steps.size)
        assertTrue(onUiThread.all { it })
        assertEquals(0, pixelUpdates)
        assertTrue(frame.pixels.all { it == 0 })
    }

    @Test
    fun `a GPU frame of each new scene finishes in steps that each draw quickly`() {
        val library = SceneLibrary()
        listOf(DemoScene.FRACTALS, DemoScene.MATERIALS).forEach { demo ->
            val description = library.load(demo)
            val start = requireNotNull(description.look.start)
            val camera = Camera(
                start.origin, start.target, Point3(0.0, 1.0, 0.0), description.look.fov, 0.0,
                (start.target - start.origin).length(), 32, 24,
            ).apply { samplesPerPixel = 3 }
            val surface = Surface.makeRaster(ImageInfo.makeN32Premul(32, 24))
            val drawMs = Collections.synchronizedList(mutableListOf<Double>())
            val done = CountDownLatch(1)
            RenderController(1_000L, 8, Random(1)).use { controller ->
                controller.mode = RenderMode.GPU
                val rayTracer = RayTracer(World(description.objects, description.look), 7)
                controller.renderImage(Frame(32, 24), rayTracer, camera, onGpuFrame = { shader, progress ->
                    // Drawn as the window draws it, and reported, so the step budget adapts as in the app.
                    val started = System.nanoTime()
                    surface.canvas.drawPaint(Paint().apply { this.shader = shader })
                    surface.makeImageSnapshot().close()
                    val ms = (System.nanoTime() - started) / 1e6
                    drawMs += ms
                    controller.gpuFrameShown(shader, ms)
                    if (progress.isDone) done.countDown()
                }) {}
                assertTrue(done.await(60, TimeUnit.SECONDS), "$demo: the GPU frame did not finish")
            }
            assertTrue(drawMs.max() < MAX_STEP_DRAW_MS, "$demo: a step took ${drawMs.max()} ms to draw")
        }
    }

    private companion object {
        const val MAX_STEP_DRAW_MS = 2_000.0
    }
}
