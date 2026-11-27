package raytracer.model

import java.awt.image.BufferedImage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Characterization test of the ray traced frame. Randomness is injected with fixed seeds (ADR 0002),
 * so the frame is deterministic regardless of thread scheduling.
 */
class TracedFrameGoldenTest {

    @Test
    fun `ray traced demo scene matches golden image`() {
        val world = World.default(Random(42))
        val camera = Camera.default().apply {
            imageWidth = 48
            imageHeight = 32
            samplesPerPixel = 2
        }
        camera.adjustYaw(0.0) // recompute projection for the new image size
        val image = BufferedImage(camera.imageWidth, camera.imageHeight, BufferedImage.TYPE_INT_ARGB)
        val done = CountDownLatch(1)

        // A huge repaint interval leaves only the final update, which signals completion.
        RenderController(Long.MAX_VALUE, 64, Random(7)).use { controller ->
            controller.renderImage(image, RayTracer(world, 5), camera) { done.countDown() }
            assertTrue(done.await(30, TimeUnit.SECONDS), "render did not finish")
        }

        assertMatchesGolden("traced.png", image)
    }

    @Test
    fun `same seeds give the same frame twice`() {
        fun render(): IntArray {
            val camera = Camera.default().apply { imageWidth = 16; imageHeight = 16; samplesPerPixel = 1 }
            camera.adjustYaw(0.0)
            val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            val done = CountDownLatch(1)
            RenderController(Long.MAX_VALUE, 8, Random(1)).use { controller ->
                controller.renderImage(image, RayTracer(World.default(Random(2)), 3), camera) { done.countDown() }
                assertTrue(done.await(30, TimeUnit.SECONDS))
            }
            return image.getRGB(0, 0, 16, 16, null, 0, 16)
        }
        assertTrue(render().contentEquals(render()))
    }
}
