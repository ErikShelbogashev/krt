package krt.domain.render

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import krt.domain.camera.Camera
import krt.domain.scene.World
import krt.i9ns.pbrt.PbrtReader
import krt.platform.math.Color
import krt.platform.skia.MetalContext
import org.junit.jupiter.api.Assumptions.assumeTrue

/** Compare a common PBRT input against an independent path integrator, in linear light. */
class PbrtAreaLightTest {
    private val scene = PbrtReader.read(checkNotNull(javaClass.getResource("/optics/area-light.pbrt")).readText())
    private val world = World(scene.objects, scene.look)
    private val camera = Camera.default().apply {
        imageWidth = SIZE
        imageHeight = SIZE
        samplesPerPixel = 48
        resetView(scene.look)
    }
    private val expected = ImageIO.read(checkNotNull(javaClass.getResource("/optics/area-light-pbrt.png")))

    @Test
    fun `diffuse CPU receiver matches PBRT at several incident angles and distances`() {
        val rng = Random(43)
        val tracer = RayTracer(world, 2)
        compare { x, y ->
            val pixel = FrameSampling.Pixel(x, SIZE - 1 - y)
            FrameSampling.samplePixel(pixel, camera, tracer, rng) / camera.samplesPerPixel.toDouble()
        }
    }

    @Test
    fun `diffuse SkSL receiver matches PBRT at several incident angles and distances`() {
        val frame = GpuFrame(world.gpuScene, camera.view(), SIZE, SIZE, 2, 48, 0.37f)
        val pixels = rasterPixels(frame)
        compare { x, y ->
            decode(pixels[y * SIZE + x]) { it * it }
        }
    }

    @Test
    fun `a completed Metal frame matches PBRT after progressive accumulation and readback`() {
        val context = MetalContext.create()
        assumeTrue(context != null, "Metal is required")
        val frame = Frame(SIZE, SIZE)
        val done = CountDownLatch(1)
        OffscreenGpu(requireNotNull(context)).use { gpu ->
            gpu.render(frame, RayTracer(world, 2), camera, Random(43), System::currentTimeMillis) {
                if (it.isDone) done.countDown()
            }
            assertTrue(done.await(9, TimeUnit.SECONDS), "Metal did not complete the frame")
        }
        compare { x, y -> decode(frame.pixels[y * SIZE + x]) { it * it } }
    }

    @Test
    fun `direct sampling respects the same path length limit on CPU and SkSL`() {
        val rng = Random(42)
        val pixel = FrameSampling.Pixel(SIZE / 2, SIZE / 2)
        val receiver = FrameSampling.samplePixel(pixel, camera, RayTracer(world, 1), rng)
        assertTrue(receiver.x == 0.0)
        val frame = GpuFrame(world.gpuScene, camera.view(), SIZE, SIZE, 1, 4, 0.37f)
        val center = rasterPixels(frame)[SIZE / 2 * SIZE + SIZE / 2]
        assertTrue(center and 0xFFFFFF == 0)
    }

    private fun compare(linear: (Int, Int) -> Color) {
        for (top in 0 until SIZE step TILE) for (left in 0 until SIZE step TILE) {
            val reference = tileMean(left, top, ::referenceLinear)
            val estimate = tileMean(left, top, linear)
            compareChannels(reference, estimate, left, top)
        }
    }

    private fun compareChannels(reference: Color, estimate: Color, left: Int, top: Int) {
        for (channel in 0..2) {
            val limit = 0.0005 + 0.04 * reference[channel]
            assertTrue(abs(estimate[channel] - reference[channel]) < limit,
                "tile ($left,$top), channel $channel: mean ${estimate[channel]}, PBRT ${reference[channel]}")
        }
    }

    private fun referenceLinear(x: Int, y: Int): Color = decode(expected.getRGB(x, y)) { srgb ->
        if (srgb <= 0.04045) srgb / 12.92 else ((srgb + 0.055) / 1.055).pow(2.4)
    }

    private fun decode(rgb: Int, linear: (Double) -> Double): Color =
        Color(linear((rgb shr 16 and 255) / 255.0), linear((rgb shr 8 and 255) / 255.0), linear((rgb and 255) / 255.0))

    private fun tileMean(left: Int, top: Int, linear: (Int, Int) -> Color): Color {
        var sum = Color.ZERO
        for (y in top until top + TILE) for (x in left until left + TILE) sum += linear(x, y)
        return sum / (TILE * TILE).toDouble()
    }

    private companion object {
        const val SIZE = 64
        const val TILE = 8
    }
}
