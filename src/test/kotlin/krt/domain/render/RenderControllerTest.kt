package krt.domain.render

import java.awt.image.BufferedImage
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import krt.domain.camera.Camera
import krt.domain.scene.World

class RenderControllerTest {

    @Test
    fun `every frame update of a ray traced render arrives on the UI thread`() {
        val camera = Camera.default().apply { imageWidth = 16; imageHeight = 16; samplesPerPixel = 1 }
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val pixels = 16 * 16
        val batch = 8
        val onUiThread = Collections.synchronizedList(mutableListOf<Boolean>())
        val done = CountDownLatch(1)
        var lastTick = 0L

        // A clock that advances on every read makes every batch publish an intermediate update.
        RenderController(0L, batch, Random(1), clock = { ++lastTick }).use { controller ->
            controller.renderImage(image, RayTracer(World.default(Random(2)), 3), camera) {
                onUiThread += SwingUtilities.isEventDispatchThread()
                if (onUiThread.size == pixels / batch + 1) done.countDown()
            }
            assertTrue(done.await(30, TimeUnit.SECONDS), "render did not finish")
        }

        assertEquals(List(pixels / batch + 1) { true }, onUiThread.toList())
    }
}
