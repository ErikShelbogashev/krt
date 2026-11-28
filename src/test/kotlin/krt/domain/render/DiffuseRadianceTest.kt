package krt.domain.render

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import krt.domain.camera.Camera
import krt.domain.scene.World
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Sky
import krt.domain.scene.model.Triangle
import krt.platform.math.Point3

class DiffuseRadianceTest {
    // For white Lambert under L_i(mu) = (1-mu)/2, L_o = integral_0^1 2*mu*L_i(mu) dmu = 1/6.
    private val up = Point3(0, 1, 0)
    private val white = Lambert(Point3.ONE)
    private val scene = World(
        listOf(Triangle(Point3(-20, 0, -20), Point3(0, 0, 20), Point3(20, 0, -20), white)),
        SceneLook(Sky(Point3.ONE, Point3.ZERO), 15.0, 0.0),
    )
    private val camera = Camera(up, Point3.ZERO, Point3(0, 0, 1), 15.0, 0.0, 1.0, SIZE, SIZE)

    @Test
    fun `CPU diffuse plane integrates a sky with known angular radiance`() {
        val tracer = RayTracer(scene, 2)
        val rng = Random(42)
        val ray = camera.ray(0.5, 0.5, rng)
        val mean = (0 until TRIALS).sumOf { tracer.color(ray, rng).x } / TRIALS
        assertRadiance(mean)
    }

    @Test
    fun `SkSL diffuse plane integrates the same sky`() {
        val frame = GpuFrame(scene.gpuScene, camera.view(), SIZE, SIZE, 2, 48, 0.37f)
        val mean = rasterPixels(frame).map { color ->
            val gamma = (color and 255) / 255.0
            gamma * gamma
        }.average()
        assertRadiance(mean)
    }

    private fun assertRadiance(mean: Double) {
        assertTrue(abs(mean - 1.0 / 6.0) < 0.006, "mean linear radiance $mean, expected 1/6")
    }

    private companion object {
        const val SIZE = 32
        const val TRIALS = 40_000
    }
}
