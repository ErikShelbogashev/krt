package krt.domain.render

import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
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
import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Sky
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.skia.MetalContext
import org.junit.jupiter.api.Assumptions.assumeTrue

class MovingGpuPreviewTest {
    @Test
    fun `a moving camera shows completed previews until the scene changes and settles to the final view`() {
        val context = MetalContext.create()
        assumeTrue(context != null, "no Metal here")
        val seeds = DraftSeeds(4)
        OffscreenGpu(requireNotNull(context)).use { gpu ->
            RenderController(0L, 8, seeds, offscreen = gpu).use { controller ->
                try {
                    moveWhileDrawing(controller, seeds)
                } finally {
                    seeds.releaseAll()
                }
            }
        }
    }

    private fun moveWhileDrawing(controller: RenderController, seeds: DraftSeeds) {
        val frame = Frame(32, 24)
        val camera = Camera.default().apply {
            imageWidth = frame.width
            imageHeight = frame.height
            samplesPerPixel = 2
            aperture = 0.0
            fov = 20.0
        }
        val world = World(look = SceneLook.CLASSIC.copy(sky = Sky(Color(1.0, 0.0, 0.0), Color(0.0, 0.0, 1.0))))
        val tracer = RayTracer(world, 2)
        val drafts = LinkedBlockingQueue<Pair<Int, Int>>()
        val done = CountDownLatch(1)
        controller.mode = RenderMode.GPU
        fun request(pose: Int, up: Double) {
            camera.lookFrom(Point3.ZERO, Point3(0.01, up, 0.0))
            controller.renderImage(frame, tracer, camera) { progress ->
                record(progress, pose, frame, drafts, done)
            }
        }

        request(1, 1.0)
        seeds.awaitStart(1)
        request(2, -1.0)
        seeds.finish(1)
        seeds.awaitStart(2)
        val sky = drafts.poll(3, TimeUnit.SECONDS)
        assertEquals(2, sky?.first)
        assertTrue(requireNotNull(sky).second and 255 > 240, "a preview looking up should see the blue sky")

        request(3, 1.0)
        seeds.finish(2)
        seeds.awaitStart(3)
        val horizon = drafts.poll(3, TimeUnit.SECONDS)
        assertEquals(3, horizon?.first)
        assertTrue((requireNotNull(horizon).second shr 16 and 255) > 240, "a preview looking down should see red")

        val green = Color(0.0, 1.0, 0.0)
        world.load(emptyList(), SceneLook.CLASSIC.copy(sky = Sky(green, green)))
        request(4, 1.0)
        seeds.finish(3)
        seeds.awaitStart(4)
        SwingUtilities.invokeAndWait { assertTrue(drafts.isEmpty(), "a retired scene published a preview") }
        seeds.finish(4)
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(4 to 0x00ff00, drafts.remove())
        assertTrue(frame.pixels.all { it and 0xffffff == 0x00ff00 })
    }

    private fun record(
        progress: RenderProgress,
        pose: Int,
        frame: Frame,
        drafts: LinkedBlockingQueue<Pair<Int, Int>>,
        done: CountDownLatch,
    ) {
        if (progress.isDraft) drafts.add(pose to (frame.pixels[frame.pixels.size / 2] and 0xffffff))
        if (progress.isDone) done.countDown()
    }

    /** Pause just after the job captured its camera, before the shader reads its seed. */
    private class DraftSeeds(count: Int) : Random() {
        private val random = Random(1)
        private val started = List(count) { CountDownLatch(1) }
        private val allowed = List(count) { CountDownLatch(1) }
        private var next = 0

        override fun nextBits(bitCount: Int): Int {
            val index = next++
            if (index < started.size) {
                started[index].countDown()
                check(allowed[index].await(5, TimeUnit.SECONDS))
            }
            return random.nextBits(bitCount)
        }

        fun awaitStart(pose: Int) = assertTrue(started[pose - 1].await(5, TimeUnit.SECONDS))
        fun finish(pose: Int) = allowed[pose - 1].countDown()
        fun releaseAll() = allowed.forEach { it.countDown() }
    }
}
