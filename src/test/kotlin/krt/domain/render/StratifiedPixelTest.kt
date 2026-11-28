package krt.domain.render

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import krt.domain.camera.Camera
import krt.domain.scene.World
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Sky
import krt.domain.scene.model.Triangle
import krt.platform.math.Point3

class StratifiedPixelTest {
    @Test
    fun `every complete progressive grid visits every stratum once regardless of scheduling`() {
        for (side in listOf(2, 4, 8, 16)) {
            for (shift in 0 until PixelStrata.CELLS) {
                val cells = (0 until side * side).map { pass ->
                    val cell = PixelStrata.cell(pass, shift)
                    val x = (side * PixelStrata.offset(cell, 0.99)).toInt()
                    val y = (side * PixelStrata.offset(cell ushr 1, 0.01)).toInt()
                    x to y
                }
                assertEquals(side * side, cells.distinct().size)
                assertTrue(cells.all { (x, y) -> x in 0 until side && y in 0 until side })
            }
        }
    }

    @Test
    fun `CPU and SkSL reduce RMS error against the exact diagonal image at the same sample count`() {
        val (camera, world) = diagonal()
        val size = camera.imageWidth
        val tracer = RayTracer(world, 1)
        camera.samplesPerPixel = 16
        val errors = listOf(false, true).map { stratified ->
            camera.stratifiedPixels = stratified
            val runs = (1..8).map { errors(it, camera, world, tracer) }
            val count = runs.size * size
            sqrt(runs.sumOf { it.cpu } / count) to sqrt(runs.sumOf { it.shader } / count)
        }
        val (ordinary, stratified) = errors
        assertTrue(stratified.first < ordinary.first * 0.8, "CPU edge RMS: $errors")
        assertTrue(stratified.second < ordinary.second * 0.8, "shader edge RMS: $errors")
    }

    @Test
    fun `splitting a stratified shader pass preserves the sample locations after the seventh sample`() {
        val (camera, world) = diagonal()
        camera.stratifiedPixels = true
        fun frame(samples: Int, taken: Int) = GpuFrame(world.gpuScene, camera.view(), camera.imageWidth,
            camera.imageHeight, 1, samples, 0.37f, GpuBand(previousSamples = taken))
        val single = rasterPixels(frame(16, 0))
        val split = rasterPixels(frame(7, 0), frame(9, 7))
        for (i in single.indices) assertTrue(abs((single[i] and 255) - (split[i] and 255)) <= 1)
    }

    private fun diagonal(): Pair<Camera, World> {
        val size = 24
        // The triangle occupies x+y>=0 on z=-1. In film coordinates every texel on the diagonal is 50% lit.
        val light = Triangle(Point3(-4, 4, -1), Point3(4, -4, -1), Point3(4, 4, -1), Emissive(Point3.ONE, 1.0))
        val world = World(listOf(light), SceneLook.CLASSIC.copy(sky = Sky(Point3.ZERO, Point3.ZERO)))
        val camera = Camera(Point3.ZERO, Point3(0, 0, -1), Point3(0, 1, 0), 90.0, 0.0, 1.0, size, size)
        return camera to world
    }

    private data class SquaredError(var cpu: Double = 0.0, var shader: Double = 0.0)

    private fun errors(seed: Int, camera: Camera, world: World, tracer: RayTracer): SquaredError {
        val size = camera.imageWidth
        val samples = camera.samplesPerPixel
        val frame = GpuFrame(world.gpuScene, camera.view(), size, size, 1, samples, seed / 17f)
        val pixels = rasterPixels(frame)
        val random = Random(seed)
        val error = SquaredError()
        for (index in pixels.indices) {
            val i = index % size
            val j = index / size
            val cpu = FrameSampling.samplePixel(FrameSampling.Pixel(i, j), camera, tracer, random).x / samples
            val shown = (pixels[(size - 1 - j) * size + i] and 255) / 255.0
            val shader = shown * shown
            if (i + j == size - 1) {
                error.cpu += (cpu - 0.5) * (cpu - 0.5)
                error.shader += (shader - 0.5) * (shader - 0.5)
            } else {
                val exact = if (i + j >= size) 1.0 else 0.0
                assertEquals(exact, cpu)
                assertTrue(abs(exact - shader) < 0.01)
            }
        }
        return error
    }
}
