package krt.scenes

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import krt.domain.camera.Camera
import krt.domain.render.Frame
import krt.domain.render.RayTracer
import krt.domain.render.RenderController
import krt.domain.scene.World
import krt.i9ns.scenefile.SceneLibrary

class MoscowCitySceneTest {
    @Test
    fun `daylight district matches the full size preview`() = compareScene("moscow-city.pbrt", "preview.png")

    @Test
    fun `illuminated district matches the night preview`() = compareScene("moscow-city-night.pbrt", "night.png")

    private fun compareScene(sceneFile: String, referenceFile: String) {
        val directory = File("samples/moscow-city")
        val scene = SceneLibrary().load(File(directory, sceneFile)).getOrThrow()
        assertTrue(scene.warnings.isEmpty(), scene.warnings.joinToString())
        val camera = Camera.default().apply {
            imageWidth = SIZE
            imageHeight = SIZE
            samplesPerPixel = 10
            resetView(scene.look)
        }
        val frame = Frame(SIZE, SIZE)
        val done = CountDownLatch(1)
        RenderController(50, 50, Random(31)).use { controller ->
            controller.renderImage(frame, RayTracer(World(scene.objects, scene.look), 10), camera) {
                if (it.isDone) done.countDown()
            }
            assertTrue(done.await(5, TimeUnit.SECONDS))
        }

        val preview = ImageIO.read(File(directory, referenceFile))
        assertEquals(1024, preview.width)
        assertEquals(1024, preview.height)
        val reference = preview.getRGB(0, 0, preview.width, preview.height, null, 0, preview.width)
        // Invert the display gamma before averaging. A subpixel lamp covers many more pixels in the full frame.
        for (y in 0 until SIZE step TILE) for (x in 0 until SIZE step TILE) for (shift in listOf(0, 8, 16)) {
            val expected = mean(reference, preview.width, x * 8, y * 8, TILE * 8, shift)
            val actual = mean(frame.pixels, SIZE, x, y, TILE, shift)
            assertTrue(abs(expected - actual) < 16, "tile ($x,$y), channel $shift: $expected vs $actual")
        }
    }

    private fun mean(rgb: IntArray, width: Int, x: Int, y: Int, side: Int, shift: Int): Double {
        var sum = 0L
        for (j in y until y + side) for (i in x until x + side) {
            val channel = rgb[j * width + i] ushr shift and 255
            sum += channel * channel
        }
        return sum.toDouble() / (255 * side * side)
    }

    private companion object {
        const val SIZE = 128
        const val TILE = 16
    }
}
