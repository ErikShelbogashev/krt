package krt.domain.render

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import krt.domain.camera.Camera
import krt.domain.scene.World
import krt.domain.scene.model.Dielectric
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Metal
import krt.domain.scene.model.Sphere
import krt.domain.trace.GpuScenePacking
import krt.platform.awt.toAWTColor
import krt.platform.math.Color
import krt.platform.math.Point3
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface

/**
 * The GPU shader runs here on Skia's CPU raster backend (ADR 0009). It has its own random numbers,
 * so it is compared with the CPU frame of the same scene by brightness, not pixel by pixel.
 */
class GpuTracerTest {

    private val world = World(
        listOf(
            Sphere(Point3(0.0, -1000.0, 0.0), 1000.0, Lambert(Color(0.5, 0.5, 0.5))),
            Sphere(Point3(0.0, 1.0, 0.0), 1.0, Dielectric(1.5)),
            Sphere(Point3(-2.5, 1.0, 0.0), 1.0, Lambert(Color(0.4, 0.2, 0.1))),
            Sphere(Point3(2.5, 1.0, 0.0), 1.0, Metal(Color(0.7, 0.6, 0.5), 0.05)),
            Sphere(Point3(0.0, 1.0, 2.5), 0.6, Emissive(Color(0.1, 0.7, 0.4), 2.0)),
        ),
    )

    private fun camera() = Camera(
        origin = Point3(0.0, 2.0, 7.0),
        target = Point3.ZERO,
        vup = Point3(0.0, 1.0, 0.0),
        fov = 60.0,
        aperture = 0.0,
        focusDist = 7.0,
        imageWidth = SIZE,
        imageHeight = SIZE,
    ).apply { samplesPerPixel = SAMPLES; adjustYaw(-Math.PI / 2); adjustPitch(-0.2) }

    @Test
    fun `GPU frame matches the CPU frame of the same scene in brightness`() {
        val camera = camera()
        val gpu = gpuFrame(camera)
        val cpu = cpuFrame(camera)

        val difference = abs(brightness(gpu) - brightness(cpu))
        assertTrue(difference < TOLERANCE, "GPU ${brightness(gpu)} vs CPU ${brightness(cpu)}")
        val regions = (0 until QUADRANTS).map { q -> abs(brightness(gpu, q) - brightness(cpu, q)) }
        assertTrue(regions.all { it < REGION_TOLERANCE }, "per quadrant $regions")
    }

    private fun gpuFrame(camera: Camera): IntArray = GpuTracer().use { tracer ->
        val frame = GpuFrame(GpuScenePacking.pack(world.objects), camera.view(), SIZE, SIZE, DEPTH, SAMPLES, 0.37f)
        val surface = Surface.makeRaster(ImageInfo.makeN32Premul(SIZE, SIZE))
        surface.canvas.drawPaint(Paint().apply { shader = tracer.shader(frame) })
        val bitmap = Bitmap.makeFromImage(surface.makeImageSnapshot())
        IntArray(SIZE * SIZE) { bitmap.getColor(it % SIZE, it / SIZE) }
    }

    private fun cpuFrame(camera: Camera): IntArray {
        val rayTracer = RayTracer(world, DEPTH)
        val frameSeed = Random(5).nextLong()
        return IntArray(SIZE * SIZE) { index ->
            val pixel = FrameSampling.Pixel(index % SIZE, SIZE - 1 - index / SIZE)
            val random = FrameSampling.pixelRandom(frameSeed, pixel, SIZE)
            FrameSampling.samplePixel(pixel, camera, rayTracer, random).toAWTColor(SAMPLES)
        }
    }

    private fun brightness(pixels: IntArray, quadrant: Int = -1): Double {
        val selected = pixels.indices.filter { quadrant < 0 || quadrantOf(it) == quadrant }
        return selected.sumOf { i ->
            val p = pixels[i]
            ((p shr 16 and 255) + (p shr 8 and 255) + (p and 255)) / 765.0
        } / selected.size
    }

    private fun quadrantOf(index: Int): Int =
        (if (index % SIZE < SIZE / 2) 0 else 1) + (if (index / SIZE < SIZE / 2) 0 else 2)

    private companion object {
        const val SIZE = 48
        const val SAMPLES = 16
        const val DEPTH = 7
        const val TOLERANCE = 0.03
        const val REGION_TOLERANCE = 0.06
        const val QUADRANTS = 4
    }
}
