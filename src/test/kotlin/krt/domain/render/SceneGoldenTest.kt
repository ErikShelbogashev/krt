package krt.domain.render

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.assertTrue
import krt.domain.camera.Camera
import krt.domain.scene.World
import krt.domain.scene.model.DemoScene
import krt.i9ns.scenefile.SceneLibrary
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

class SceneGoldenTest {
    @TestFactory
    fun scenes() = DemoScene.entries.flatMap { demo ->
        val name = referenceFor(demo)
        listOf(
            dynamicTest("${demo.title} CPU golden") {
                val (world, camera) = load(demo)
                val frame = Frame(WIDTH, HEIGHT)
                val done = CountDownLatch(1)
                RenderController(Long.MAX_VALUE, 64, Random(7)).use { controller ->
                    controller.renderImage(frame, RayTracer(world, DEPTH), camera) {
                        if (it.isDone) done.countDown()
                    }
                    assertTrue(done.await(9, TimeUnit.SECONDS), "${demo.title} render timed out")
                }
                assertMatchesGolden(name, frame)
            },
            dynamicTest("${demo.title} SkSL matches CPU golden regions") {
                val (world, camera) = load(demo)
                val golden = ImageIO.read(File("src/test/resources/golden/$name"))
                val frame = GpuFrame(world.gpuScene, camera.view(), WIDTH, HEIGHT, DEPTH, SAMPLES, 0.37f)
                val actual = rasterPixels(frame)
                val reference = golden.getRGB(0, 0, WIDTH, HEIGHT, null, 0, WIDTH)
                compareRegions(reference, actual)
            },
        )
    }

    private fun referenceFor(demo: DemoScene): String {
        val linuxX64 = System.getProperty("os.name") == "Linux" && System.getProperty("os.arch") in X64
        val directory = if (demo == DemoScene.CANDLE && linuxX64) "scenes/linux-x64" else "scenes"
        return "$directory/${demo.path.substringBeforeLast('.')}.png"
    }

    private fun load(demo: DemoScene): Pair<World, Camera> {
        val scene = SceneLibrary().load(demo)
        val camera = Camera.default().apply {
            imageWidth = WIDTH
            imageHeight = HEIGHT
            samplesPerPixel = SAMPLES
            resetView(scene.look)
        }
        return World(scene.objects, scene.look) to camera
    }

    private fun rasterPixels(frame: GpuFrame): IntArray {
        val info = ImageInfo.makeN32Premul(WIDTH, HEIGHT)
        return Surface.makeRaster(info).use { surface ->
            GpuTracer().use { tracer ->
                tracer.hdrShader(frame).use { shader -> draw(surface, shader) }
            }
            surface.makeImageSnapshot().use { image ->
                Bitmap.makeFromImage(image).use { bitmap ->
                    IntArray(WIDTH * HEIGHT) { bitmap.getColor(it % WIDTH, it / WIDTH) }
                }
            }
        }
    }

    private fun draw(surface: Surface, shader: Shader) {
        Paint().use { paint ->
            paint.shader = shader
            surface.canvas.drawPaint(paint)
        }
    }

    /** Independent ray sequences produce different noise. Compare local colour in display-space RGB. */
    private fun compareRegions(expected: IntArray, actual: IntArray) {
        for (top in 0 until HEIGHT step TILE) for (left in 0 until WIDTH step TILE) {
            val origin = top * WIDTH + left
            for (shift in listOf(16, 8, 0)) {
                compareChannel(expected, actual, origin, shift)
            }
        }
    }

    private fun compareChannel(expected: IntArray, actual: IntArray, origin: Int, shift: Int) {
        var sum = 0.0
        var squaredSum = 0.0
        for (row in 0 until TILE) for (col in 0 until TILE) {
            val i = origin + row * WIDTH + col
            val delta = ((actual[i] shr shift) and 255) - ((expected[i] shr shift) and 255)
            sum += delta
            squaredSum += delta * delta
        }
        val count = TILE * TILE
        val mean = sum / count
        val variance = ((squaredSum - sum * mean) / (count - 1)).coerceAtLeast(0.0)
        // Retain a tight limit in flat colours and allow for Monte Carlo noise in fog and specular highlights.
        val limit = BASE_TOLERANCE + STANDARD_ERRORS * sqrt(variance / count)
        assertTrue(abs(mean) <= limit, "tile (${origin % WIDTH},${origin / WIDTH}), RGB shift $shift: $mean ± $limit")
    }

    private companion object {
        val X64 = setOf("amd64", "x86_64")
        const val WIDTH = 64
        const val HEIGHT = 40
        const val SAMPLES = 24
        const val DEPTH = 7
        const val TILE = 8
        const val BASE_TOLERANCE = 3.0
        const val STANDARD_ERRORS = 4.0
    }
}
