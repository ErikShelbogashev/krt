package krt.scenes

import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertTrue
import krt.domain.camera.Camera
import krt.domain.render.GpuFrame
import krt.domain.render.GpuTracer
import krt.domain.scene.World
import krt.domain.scene.model.DemoScene
import krt.domain.scene.model.Fog
import krt.domain.scene.model.SceneDescription
import krt.i9ns.scenefile.SceneLibrary
import krt.platform.math.Color
import krt.platform.math.Point3
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface

/** Evaluate cropped, full-resolution SkSL samples on Skia's raster backend. */
class ShowcaseShaderOpticsTest {
    private val scenes = SceneLibrary()

    @Test
    fun `the shader separates the slit into bright red green and blue bands in order`() {
        val scene = scenes.load(DemoScene.PRISM)
        GpuTracer().use { tracer ->
            val view = View(scene, 3, tracer)
            val red = view.patch(159..161, 82..87)
            val green = view.patch(165..167, 82..87)
            val blue = view.patch(174..177, 82..87)
            assertTrue(red.x > 2 * max(red.y, red.z), "red probe: $red")
            assertTrue(green.y > 2 * max(green.x, green.z), "green probe: $green")
            assertTrue(blue.z > 2 * max(blue.x, blue.y), "blue probe: $blue")
            assertTrue(minOf(red.x, green.y, blue.z) > 0.20, "displayed RGB signals: $red, $green, $blue")
        }
    }

    @Test
    fun `the shader shows greater in-scattering in the tunnel shaft than in the adjacent shadow`() {
        val scene = scenes.load(DemoScene.TEMPLE)
        val vacuum = scene.copy(objects = scene.objects.filterNot { it is Fog })
        GpuTracer().use { tracer ->
            val wet = View(scene, 4, tracer)
            val dry = View(vacuum, 4, tracer)
            val shaft = wet.patch(160..163, 96..99) - dry.patch(160..163, 96..99)
            val shadow = wet.patch(194..197, 96..99) - dry.patch(194..197, 96..99)
            val light = shaft.dot(LUMA)
            assertTrue(light > 0.08, "light added by haze: $light")
            assertTrue(light > 1.5 * shadow.dot(LUMA), "shaft $light vs shadow ${shadow.dot(LUMA)}")
        }
    }

    private class View(scene: SceneDescription, private val depth: Int, private val tracer: GpuTracer) {
        private val camera = Camera.default().apply {
            imageWidth = WIDTH
            imageHeight = HEIGHT
            resetView(scene.look)
        }.view()
        private val world = World(scene.objects, scene.look).gpuScene

        fun patch(xs: IntRange, ys: IntRange): Color {
            val info = ImageInfo.makeN32Premul(xs.count(), ys.count())
            return Surface.makeRaster(info).use { target ->
                target.canvas.translate(-xs.first.toFloat(), -ys.first.toFloat())
                var sum = Point3.ZERO
                for (seed in SEEDS) {
                    val frame = GpuFrame(world, camera, WIDTH, HEIGHT, depth, 32, seed)
                    draw(frame, target)
                    sum += target.makeImageSnapshot().use { image -> Bitmap.makeFromImage(image).use(::linearSum) }
                }
                sum / (info.width * info.height * SEEDS.size).toDouble()
            }
        }

        private fun draw(frame: GpuFrame, target: Surface) {
            tracer.hdrShader(frame).use { shader ->
                Paint().use { paint ->
                    paint.shader = shader
                    target.canvas.drawPaint(paint)
                }
            }
        }

        /** Undo the display gamma before averaging. Highlights in the display copy remain clipped. */
        private fun linearSum(bitmap: Bitmap): Color {
            var sum = Point3.ZERO
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                val rgb = bitmap.getColor(x, y)
                sum += Color(linear(rgb, 16), linear(rgb, 8), linear(rgb, 0))
            }
            return sum
        }

        private fun linear(rgb: Int, shift: Int): Double {
            val displayed = (rgb ushr shift and 255) / 255.0
            return displayed * displayed
        }
    }

    private companion object {
        const val WIDTH = 320
        const val HEIGHT = 180
        val SEEDS = listOf(0.071f, 0.31f, 0.49f, 0.83f)
        val LUMA = Color(0.2126, 0.7152, 0.0722)
    }
}
