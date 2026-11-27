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
    fun `in GPU mode the frame is handed over as a shader and the pixel buffer is left alone`() {
        val camera = Camera.default().apply { imageWidth = 16; imageHeight = 16; samplesPerPixel = 1 }
        val frame = Frame(16, 16)
        val shaders = mutableListOf<org.jetbrains.skia.Shader>()
        var pixelUpdates = 0

        RenderController(0L, 8, Random(1)).use { controller ->
            controller.mode = RenderMode.GPU
            val rayTracer = RayTracer(World.default(Random(2)), 3)
            controller.renderImage(frame, rayTracer, camera, onGpuFrame = { shaders += it }) { pixelUpdates++ }
        }

        assertEquals(1, shaders.size)
        assertEquals(0, pixelUpdates)
        assertTrue(frame.pixels.all { it == 0 })
    }
}
