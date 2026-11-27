package krt.domain.render

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import krt.domain.camera.Camera
import krt.domain.render.model.RenderMode
import krt.domain.render.model.RenderProgress
import krt.domain.scene.World

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
                if (onUiThread.size == pixels / batch + 1) done.countDown()
            }
            assertTrue(done.await(30, TimeUnit.SECONDS), "render did not finish")
        }

        assertEquals(List(pixels / batch + 1) { true }, onUiThread.toList())
    }

    @Test
    fun `a progressive frame publishes after every batch of every pass`() {
        val passes = 3
        val camera = Camera.default().apply { imageWidth = 16; imageHeight = 16; samplesPerPixel = passes }
        val frame = Frame(16, 16)
        val batch = 32
        val expected = passes * (16 * 16 / batch) + 1
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
        val expected = passes * (16 * 16 / batch) + 1
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

        assertEquals(listOf(0, 1, 2, 4, 5), steps.map { it.completedPasses }) // a quick look first
        assertTrue(onUiThread.all { it })
        assertEquals(0, pixelUpdates)
        assertTrue(frame.pixels.all { it == 0 })
    }
}
