package krt.domain.render

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import krt.domain.camera.Camera
import krt.domain.render.model.RenderMode
import krt.domain.scene.World
import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Sky
import krt.platform.math.Color
import krt.platform.skia.MetalContext
import org.junit.jupiter.api.Assumptions.assumeTrue

/** GPU frames on a context of the renderer's own; on a machine without Metal there is none, and the test skips. */
class OffscreenGpuTest {

    @Test
    fun `a GPU frame drawn offscreen comes into the frame's pixels, as bright as the CPU frame`() {
        val context = MetalContext.create()
        assumeTrue(context != null, "no Metal here")
        val world = World.default(Random(2))
        val camera = Camera.default().apply { imageWidth = 64; imageHeight = 48; samplesPerPixel = 16 }
        val gpu = Frame(64, 48)
        val done = CountDownLatch(1)
        var shaders = 0
        OffscreenGpu(requireNotNull(context)).use { offscreen ->
            RenderController(0L, 8, Random(1), offscreen = offscreen).use { controller ->
                controller.mode = RenderMode.GPU
                controller.renderImage(gpu, RayTracer(world, 5), camera, onGpuFrame = { _, _ -> shaders++ }) {
                    if (it.isDone) done.countDown()
                }
                assertTrue(done.await(30, TimeUnit.SECONDS), "the GPU frame did not finish")
            }
        }
        val cpu = Frame(64, 48)
        val cpuDone = CountDownLatch(1)
        RenderController(0L, 8, Random(1)).use { controller ->
            controller.renderImage(cpu, RayTracer(world, 5), camera) { if (it.isDone) cpuDone.countDown() }
            assertTrue(cpuDone.await(30, TimeUnit.SECONDS))
        }
        assertTrue(shaders == 0, "the window got shaders to draw")
        val (onGpu, onCpu) = brightness(gpu) to brightness(cpu)
        assertTrue(onGpu > 0.05, "the GPU frame is black")
        assertTrue(kotlin.math.abs(onGpu - onCpu) < 0.05, "GPU $onGpu vs CPU $onCpu")
    }

    @Test
    fun `switching scenes during refinement replaces every pixel on one Metal renderer`() {
        val context = MetalContext.create()
        assumeTrue(context != null, "no Metal here")
        OffscreenGpu(requireNotNull(context)).use { gpu ->
            RenderController(0L, 8, Random(1), offscreen = gpu).use(::switchScenes)
        }
    }

    private fun switchScenes(controller: RenderController) {
        val camera = Camera.default().apply { imageWidth = 32; imageHeight = 24; samplesPerPixel = 3 }
        val frame = Frame(camera.imageWidth, camera.imageHeight)
        val world = World()
        val tracer = RayTracer(world, 2)
        val red = Color(1.0, 0.0, 0.0) to 0xff0000
        val blue = Color(0.0, 0.0, 1.0) to 0x0000ff
        controller.mode = RenderMode.GPU
        for ((color, rgb) in listOf(red, blue, red, blue)) {
            val draft = CountDownLatch(1)
            world.load(emptyList(), SceneLook.CLASSIC)
            controller.renderImage(frame, tracer, camera) { if (it.isDraft) draft.countDown() }
            assertTrue(draft.await(5, TimeUnit.SECONDS))
            world.load(emptyList(), SceneLook.CLASSIC.copy(sky = Sky(color, color)))
            val done = CountDownLatch(1)
            controller.renderImage(frame, tracer, camera) { if (it.isDone) done.countDown() }
            assertTrue(done.await(5, TimeUnit.SECONDS))
            assertTrue(frame.pixels.all { it and 0xffffff == rgb }, "stale pixels in the $color scene")
        }
    }

    @Test
    fun `a stalled event thread receives only the latest GPU update`() {
        val context = MetalContext.create()
        assumeTrue(context != null, "no Metal here")
        val camera = Camera.default().apply { imageWidth = 32; imageHeight = 24; samplesPerPixel = 2 }
        val release = CountDownLatch(1)
        val callbacks = mutableListOf<Boolean>()
        OffscreenGpu(requireNotNull(context)).use { gpu ->
            SwingUtilities.invokeLater { release.await(8, TimeUnit.SECONDS) }
            try {
                val job = gpu.render(
                    Frame(32, 24), RayTracer(World(), 2), camera, Random(5), System::currentTimeMillis,
                ) {
                    callbacks.add(it.isDone)
                }
                runBlocking { withTimeout(8_000L) { job.join() } }
            } finally {
                release.countDown()
            }
            SwingUtilities.invokeAndWait { assertEquals(listOf(true), callbacks) }
        }
    }

    @Test
    fun `a retired GPU frame cannot overwrite the next renderer's pixels`() {
        val context = MetalContext.create()
        assumeTrue(context != null, "no Metal here")
        val camera = Camera.default().apply { imageWidth = 32; imageHeight = 24; samplesPerPixel = 2 }
        val pixels = Frame(32, 24)
        val draft = CountDownLatch(1)
        OffscreenGpu(requireNotNull(context)).use { gpu ->
            val job = gpu.render(pixels, RayTracer(World(), 2), camera, Random(5), System::currentTimeMillis) {
                if (it.isDraft) draft.countDown()
            }
            assertTrue(draft.await(5, TimeUnit.SECONDS))
            gpu.invalidateFrame()
            pixels.pixels.fill(0x123456)
            runBlocking { withTimeout(5_000L) { job.join() } }
            assertTrue(pixels.pixels.all { it == 0x123456 })
        }
    }

    @Test
    fun `scaled quick looks keep up with scene changes before refinement`() {
        val context = MetalContext.create()
        assumeTrue(context != null, "no Metal here")
        val frame = Frame(641, 361)
        val camera = Camera.default().apply { imageWidth = frame.width; imageHeight = frame.height }
        OffscreenGpu(requireNotNull(context)).use { gpu ->
            for ((color, rgb) in listOf(Color(1.0, 0.0, 0.0) to 0xff0000, Color(0.0, 0.0, 1.0) to 0xff)) {
                val tracer = RayTracer(World(look = SceneLook.CLASSIC.copy(sky = Sky(color, color))), 2)
                val draft = CountDownLatch(1)
                val job = gpu.render(frame, tracer, camera, Random(2), System::currentTimeMillis) {
                    if (it.isDraft) draft.countDown()
                }
                assertTrue(draft.await(5, TimeUnit.SECONDS))
                job.cancel()
                assertTrue(frame.pixels.all { it and 0xffffff == rgb })
            }
        }
    }

    private fun brightness(frame: Frame): Double =
        frame.pixels.sumOf { ((it shr 16 and 255) + (it shr 8 and 255) + (it and 255)) / 765.0 } / frame.pixels.size
}
