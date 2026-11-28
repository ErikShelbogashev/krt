package krt.domain.render

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import krt.domain.camera.Camera
import krt.domain.scene.World
import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Sky
import krt.domain.trace.GpuScene
import krt.platform.math.Color
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface

/** The paint target of a window's cached pictures is eight-bit premultiplied RGBA, including in this raster run. */
class WindowAccumulationTest {
    private val view = Camera.default().view()

    @Test
    fun `a long accumulation has the same mean with bright or dark samples first`() {
        val dark = constant(0.0)
        val bright = constant(1.0)
        fun alternating(first: GpuScene, last: GpuScene) = buildList {
            add(frame(first, 64, 0))
            add(frame(first, 64, 64))
            for (previous in 128 until 256) add(frame(last, 1, previous))
        }
        for (pixels in listOf(window(alternating(dark, bright)), window(alternating(bright, dark)))) {
            // Invert the display transfer for each pixel before estimating the spatial mean and noise.
            val linear = pixels.map { val gamma = (it and 255) / 255.0; gamma * gamma }
            assertEquals(0.5, linear.average(), 0.008)
            val rms = kotlin.math.sqrt(linear.sumOf { (it - 0.5) * (it - 0.5) } / linear.size)
            assertTrue(rms < 0.035, "linear RMS $rms")
        }
    }

    @Test
    fun `a uniform fractional light stays within one display code over 256 single sample updates`() {
        val sky = constant(0.37)
        val pixels = window((0 until 256).map { frame(sky, 1, it) })
        val expected = 255 * kotlin.math.sqrt(0.37)
        assertTrue(pixels.all { abs((it and 255) - expected) < 1.01 })
    }

    @Test
    fun `splitting a pass into bands preserves the exact rays and the displayed pixels`() {
        val sky = World(look = SceneLook.CLASSIC.copy(sky = Sky(Color.ZERO, Color.ONE))).gpuScene
        val whole = listOf(frame(sky, 3, 0), frame(sky, 5, 3))
        val bands = whole.flatMap { pass ->
            listOf(0 until SIZE / 2, SIZE / 2 until SIZE).map { rows ->
                frame(sky, pass.samples, pass.band.previousSamples, rows)
            }
        }
        val reference = window(whole)
        assertContentEquals(reference, window(bands))
        assertContentEquals(reference, window(whole))
        // A new first pass owns its samples, even on the same picture chain.
        assertContentEquals(window(listOf(whole.first())), window(bands + whole.first()))
    }

    private fun constant(value: Double): GpuScene {
        val color = Color.ONE * value
        return World(look = SceneLook.CLASSIC.copy(sky = Sky(color, color))).gpuScene
    }

    private fun frame(scene: GpuScene, samples: Int, previous: Int, rows: IntRange = 0 until SIZE) =
        GpuFrame(scene, view, SIZE, SIZE, 2, samples, 0.37f, GpuBand(previous, rows))

    private fun window(frames: List<GpuFrame>): IntArray {
        val surface = Surface.makeRaster(ImageInfo.makeN32Premul(SIZE, SIZE))
        surface.use {
            GpuTracer().use { tracer ->
                tracer.WindowSteps().use { steps -> draw(surface, steps, frames) }
            }
            return surface.makeImageSnapshot().use { image ->
                Bitmap.makeFromImage(image).use { bitmap ->
                    IntArray(SIZE * SIZE) { bitmap.getColor(it % SIZE, it / SIZE) }
                }
            }
        }
    }

    private fun draw(surface: Surface, steps: GpuTracer.WindowSteps, frames: List<GpuFrame>) {
        Paint().use { paint ->
            for (frame in frames) steps.next(frame).use { shader ->
                paint.shader = shader
                surface.canvas.drawPaint(paint)
            }
        }
    }

    private companion object {
        const val SIZE = 16
    }
}
