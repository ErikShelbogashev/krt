package krt.domain.render

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import krt.domain.camera.Camera
import krt.domain.render.model.RenderMode
import krt.domain.scene.World
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

    private fun brightness(frame: Frame): Double =
        frame.pixels.sumOf { ((it shr 16 and 255) + (it shr 8 and 255) + (it and 255)) / 765.0 } / frame.pixels.size
}
