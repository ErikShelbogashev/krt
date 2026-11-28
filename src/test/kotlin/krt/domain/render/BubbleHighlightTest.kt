package krt.domain.render

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import krt.domain.camera.Camera
import krt.domain.scene.World
import krt.domain.scene.model.DemoScene
import krt.i9ns.scenefile.SceneLibrary
import krt.platform.math.PixelDifferentials

class BubbleHighlightTest {
    @Test
    fun `neighbouring pixels in the window reflection have independent sampling noise`() {
        val scene = SceneLibrary().load(DemoScene.BUBBLES)
        val camera = Camera.default().apply {
            imageWidth = 1024
            imageHeight = 1024
            resetView(scene.look)
            stratifiedPixels = true
        }
        val tracer = RayTracer(World(scene.objects, scene.look), 8)
        // Subtract two independent renders of the same patch. Their difference removes the window's actual edges.
        val error = DoubleArray(SIDE * SIDE) { i ->
            val pixel = FrameSampling.Pixel(191 + i % SIDE, 1023 - 467 - i / SIDE)
            sample(camera, tracer, pixel, 31L) - sample(camera, tracer, pixel, 74923L)
        }
        var horizontal = 0.0
        var vertical = 0.0
        var energy = 0.0
        for (y in 0 until SIDE - 1) for (x in 0 until SIDE - 1) {
            val i = y * SIDE + x
            horizontal += error[i] * error[i + 1]
            vertical += error[i] * error[i + SIDE]
            energy += error[i] * error[i]
        }
        assertTrue(energy > 0.0)
        assertTrue(abs(horizontal / energy) < 0.2 && abs(vertical / energy) < 0.2,
            "Neighbour correlation: x=${horizontal / energy}, y=${vertical / energy}")
    }

    private fun sample(camera: Camera, tracer: RayTracer, pixel: FrameSampling.Pixel, seed: Long): Double {
        val width = camera.imageWidth
        val shift = PixelStrata.shift(seed, pixel.j * width + pixel.i)
        val footprint = camera.footprint(PixelDifferentials.sampleWidth(SAMPLES))
        var sum = 0.0
        repeat(SAMPLES) { pass ->
            val random = FrameSampling.passRandom(seed, pixel, width, camera.imageHeight, pass)
            val cell = PixelStrata.cell(pass, shift)
            sum += FrameSampling.stratifiedSample(pixel, camera, tracer, random, cell, footprint).x
        }
        return sum / SAMPLES
    }

    private companion object {
        const val SAMPLES = 128
        const val SIDE = 32
    }
}
