package krt.domain.render

import java.awt.Point
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import krt.domain.camera.Camera
import krt.domain.scene.World
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Sky
import krt.domain.scene.model.Triangle
import krt.platform.math.Color
import krt.platform.math.Point3

class PixelCoverageTest {
    private fun pinhole(width: Int, height: Int) =
        Camera(Point3.ZERO, Point3(0, 0, -1), Point3(0, 1, 0), 90.0, 0.0, 1.0, width, height)

    @Test
    fun `projection inverts rays through the first middle and last pixels at odd and even sizes`() {
        for (size in listOf(8, 9)) {
            val camera = pinhole(size, size + 2)
            for (x in listOf(0, size / 2, size - 1)) {
                for (y in listOf(0, camera.imageHeight / 2, camera.imageHeight - 1)) {
                    val ray = camera.ray((x + 0.5) / size, 1 - (y + 0.5) / camera.imageHeight, Random(1))
                    assertEquals(Point(x, y), camera.project(ray.at(2.0)))
                }
            }
        }
    }

    @Test
    fun `a diagonal bisecting the last film pixel covers half of it on both engines`() {
        // For this square pinhole at z = -1 the corner pixel covers [0.75, 1]².
        // The light starts at x+y = 1.75, exactly half of that square.
        val lamp = Triangle(
            Point3(0.0, 1.75, -1.0), Point3(1.75, 0.0, -1.0), Point3(3, 3, -1), Emissive(Color.ONE, 1.0),
        )
        val world = World(listOf(lamp), SceneLook.CLASSIC.copy(sky = Sky(Color.ZERO, Color.ZERO)))
        val camera = pinhole(8, 8).apply { samplesPerPixel = 4096 }
        val cpu = FrameSampling.samplePixel(FrameSampling.Pixel(7, 7), camera, RayTracer(world, 1), Random(9))
        assertEquals(0.5, cpu.x / camera.samplesPerPixel, 0.025)
        val gpu = (1..12).map { seed ->
            val frame = GpuFrame(world.gpuScene, camera.view(), 8, 8, 1, 64, seed / 13f)
            val row = rasterPixels(frame)
            assertEquals(0, row[6] and 255)
            val shown = (row[7] and 255) / 255.0
            shown * shown
        }
        assertEquals(0.5, gpu.average(), 0.065)
    }
}
