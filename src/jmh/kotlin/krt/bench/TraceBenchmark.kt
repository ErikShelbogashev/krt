package krt.bench

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import krt.domain.camera.Camera
import krt.domain.render.Frame
import krt.domain.render.FrameSampling
import krt.domain.render.RayTracer
import krt.domain.render.RenderController
import krt.domain.scene.World
import krt.platform.math.Ray
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.infra.Blackhole

/**
 * Cost of the render hot path: one ray–scene intersection, one frame on one thread
 * and one frame through [RenderController] on all cores.
 */
@Suppress("unused") // JMH finds and runs the benchmarks by reflection
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
open class TraceBenchmark {

    @Param(Scenes.DEMO, Scenes.DEMO_WITH_MODEL)
    lateinit var scene: String

    private lateinit var world: World
    private lateinit var camera: Camera
    private lateinit var rayTracer: RayTracer
    private lateinit var pixels: List<FrameSampling.Pixel>
    private lateinit var primaryRays: List<Ray>
    private lateinit var renderController: RenderController
    private lateinit var frame: Frame

    @Setup(Level.Trial)
    fun setUp() {
        world = Scenes.world(scene)
        camera = Scenes.camera()
        rayTracer = RayTracer(world, Scenes.MAX_DEPTH)
        pixels = FrameSampling.pixelOrder(camera.imageWidth, camera.imageHeight, Random(PIXEL_ORDER_SEED))
        primaryRays = List(PRIMARY_RAYS) { i ->
            val random = Random(i)
            camera.ray(random.nextDouble(), random.nextDouble(), random)
        }
        renderController = RenderController(Long.MAX_VALUE, BATCH_SIZE, Random(PIXEL_ORDER_SEED))
        frame = Frame(camera.imageWidth, camera.imageHeight)
    }

    @TearDown(Level.Trial)
    fun tearDown() {
        renderController.close()
    }

    /** [PRIMARY_RAYS] intersections of camera rays with the scene. */
    @Benchmark
    fun primaryRayHits(blackhole: Blackhole) {
        for (ray in primaryRays) blackhole.consume(world.hit(ray, T_MIN, Double.POSITIVE_INFINITY))
    }

    /** A whole frame on the benchmark thread: tracing cost without scheduling. */
    @Benchmark
    fun frameSingleThread(blackhole: Blackhole) {
        for (pixel in pixels) {
            val random = FrameSampling.pixelRandom(FRAME_SEED, pixel, camera.imageWidth)
            blackhole.consume(FrameSampling.samplePixel(pixel, camera, rayTracer, random))
        }
    }

    /** A whole frame through the app's renderer on all cores: tracing plus scheduling. */
    @Benchmark
    fun frameRenderController() {
        val done = CountDownLatch(1)
        renderController.renderImage(frame, rayTracer, camera) { done.countDown() }
        check(done.await(FRAME_TIMEOUT_S, TimeUnit.SECONDS)) { "frame did not finish" }
    }

    private companion object {
        const val PRIMARY_RAYS = 10_000
        const val PIXEL_ORDER_SEED = 7
        const val FRAME_SEED = 1L
        const val BATCH_SIZE = 50
        const val T_MIN = 0.001
        const val FRAME_TIMEOUT_S = 600L
    }
}
