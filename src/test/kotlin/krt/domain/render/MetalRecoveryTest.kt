package krt.domain.render

import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import krt.domain.camera.Camera
import krt.domain.render.model.RenderMode
import krt.domain.render.model.RenderProgress
import krt.domain.scene.World
import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Sky
import krt.platform.math.Color
import krt.platform.skia.MetalContext
import org.jetbrains.skia.DirectContext
import org.junit.jupiter.api.Assumptions.assumeTrue

class MetalRecoveryTest {
    @Test
    fun `losing Metal during refinement restarts the preview and completes the frame`() {
        val initial = metal()
        var steps = 0
        var replacements = 0
        val frame = Frame(32, 24)
        OffscreenGpu(initial, { metal().also { replacements++ } }) {
            // Both surfaces exist after the preview and the first full-resolution step.
            if (++steps == 2) initial.abandon()
        }.use { gpu ->
            val progress = render(gpu, frame)
            assertNull(progress.last().failure)
            assertTrue(progress.last().isDone)
            assertEquals(1, replacements)
            assertTrue(frame.pixels.all { it and 0xffffff == 0xff00ff })
        }
    }

    @Test
    fun `two abandoned contexts report failure and a later request starts with a fresh context`() {
        val initial = metal().also { it.abandon() }
        var replacements = 0
        val frame = Frame(32, 24)
        val replacement = {
            metal().also { if (++replacements == 1) it.abandon() }
        }
        OffscreenGpu(initial, replacement) {}.use { gpu ->
            assertNotNull(render(gpu, frame).last().failure)
            assertEquals(1, replacements)
            val next = render(gpu, frame)
            assertTrue(next.last().isDone)
            assertEquals(2, replacements)
            assertTrue(frame.pixels.all { it and 0xffffff == 0xff00ff })
        }
    }

    @Test
    fun `returning from wireframe renders the next scene on a fresh Metal context`() {
        val initial = metal()
        var replacements = 0
        val frame = Frame(32, 24)
        val camera = Camera.default().apply {
            imageWidth = frame.width
            imageHeight = frame.height
            samplesPerPixel = 4
        }
        val red = Color(1.0, 0.0, 0.0)
        val green = Color(0.0, 1.0, 0.0)
        val world = World(look = SceneLook.CLASSIC.copy(sky = Sky(red, red)))
        val tracer = RayTracer(world, 2)
        OffscreenGpu(initial, { metal().also { replacements++ } }) {}.use { gpu ->
            RenderController(0L, 1, Random(3), offscreen = gpu).use { controller ->
                val draft = CountDownLatch(1)
                controller.mode = RenderMode.GPU
                controller.renderImage(frame, tracer, camera) { if (it.isDraft) draft.countDown() }
                assertTrue(draft.await(5, TimeUnit.SECONDS))
                assertTrue(frame.pixels.all { it and 0xffffff == 0xff0000 })

                controller.mode = RenderMode.WIREFRAME
                repeat(3) { controller.renderImage(frame, tracer, camera) {} }
                world.load(emptyList(), SceneLook.CLASSIC.copy(sky = Sky(green, green)))
                val done = CountDownLatch(1)
                controller.mode = RenderMode.GPU
                controller.renderImage(frame, tracer, camera) { if (it.isDone) done.countDown() }
                assertTrue(done.await(5, TimeUnit.SECONDS))
                assertEquals(1, replacements)
                assertTrue(initial.isClosed)
                assertTrue(frame.pixels.all { it and 0xffffff == 0x00ff00 })
            }
        }
    }

    private fun metal(): DirectContext {
        val context = MetalContext.create()
        assumeTrue(context != null, "no Metal here")
        return requireNotNull(context)
    }

    private fun render(gpu: OffscreenGpu, frame: Frame): List<RenderProgress> {
        val color = Color(1.0, 0.0, 1.0)
        val tracer = RayTracer(World(look = SceneLook.CLASSIC.copy(sky = Sky(color, color))), 2)
        val camera = Camera.default().apply {
            imageWidth = frame.width
            imageHeight = frame.height
            samplesPerPixel = 4
        }
        val updates = LinkedBlockingQueue<RenderProgress>()
        gpu.render(frame, tracer, camera, Random(2), System::currentTimeMillis, updates::add)
        val events = mutableListOf<RenderProgress>()
        do {
            val progress = assertNotNull(updates.poll(5, TimeUnit.SECONDS), "missing render completion or failure")
            events.add(progress)
        } while (!progress.isDone && progress.failure == null)
        return events
    }
}
