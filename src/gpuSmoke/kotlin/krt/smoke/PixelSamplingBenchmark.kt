package krt.smoke

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sqrt
import kotlin.random.Random
import krt.domain.camera.Camera
import krt.domain.render.Frame
import krt.domain.render.OffscreenGpu
import krt.domain.render.RayTracer
import krt.domain.render.RenderController
import krt.domain.render.model.RenderMode
import krt.domain.scene.World
import krt.domain.scene.model.DemoScene
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Sky
import krt.domain.scene.model.Triangle
import krt.i9ns.scenefile.SceneLibrary
import krt.platform.math.Point3
import krt.platform.skia.MetalContext

/** Compares displayed images at the same wall-clock deadline, including preview and refinement. */
internal object PixelSamplingBenchmark {
    private const val SIZE = 128
    private const val BUDGET_MS = 600L
    private const val REFERENCE_SPP = 512

    fun run(filtered: Boolean) {
        val materials = SceneLibrary().load(DemoScene.MATERIALS)
        val start = requireNotNull(materials.look.start)
        val scene = Camera(start.origin, start.target, Point3(0, 1, 0), materials.look.fov, materials.look.aperture,
            (start.origin - start.target).length(), SIZE, SIZE) to RayTracer(World(materials.objects, materials.look), 6)
        val triangle = Triangle(Point3(-4, 4, -1), Point3(4, -4, -1), Point3(4, 4, -1), Emissive(Point3.ONE, 1.0))
        val diagonal = Camera(Point3.ZERO, Point3(0, 0, -1), Point3(0, 1, 0), 90.0, 0.0, 1.0, SIZE, SIZE) to
            RayTracer(World(listOf(triangle), SceneLook.CLASSIC.copy(sky = Sky(Point3.ZERO, Point3.ZERO))), 1)
        OffscreenGpu(requireNotNull(MetalContext.create())).use { gpu ->
            for (engine in listOf(RenderMode.CPU, RenderMode.GPU)) {
                RenderController(10L, 64, Random(31), offscreen = gpu).use { renderer ->
                    renderer.mode = engine
                    if (!filtered) compare(renderer, diagonal, true)
                    scene.first.filterWood = filtered
                    compare(renderer, scene, false)
                }
            }
        }
    }

    private fun compare(renderer: RenderController, scene: Pair<Camera, RayTracer>, diagonal: Boolean) {
        val (camera, tracer) = scene
        camera.samplesPerPixel = 8
        camera.stratifiedPixels = false
        capture(renderer, camera, tracer)
        camera.samplesPerPixel = REFERENCE_SPP
        val reference = if (diagonal) IntArray(SIZE * SIZE) { 0xffb4b4b4.toInt() } else capture(renderer, camera, tracer).pixels
        camera.samplesPerPixel = 256
        val label = if (diagonal) "Edge" else if (camera.filterWood) "Materials-filtered" else "Materials"
        for (trial in 0 until 4) {
            for (stratified in listOf(trial % 2 == 1, trial % 2 == 0)) {
                camera.stratifiedPixels = stratified
                val shot = capture(renderer, camera, tracer, BUDGET_MS)
                val measured = reference.indices.filter { !diagonal || it / SIZE == it % SIZE }
                val meanSquare = measured.sumOf { p ->
                    listOf(0, 8, 16).sumOf { shift ->
                        val delta = ((shot.pixels[p] ushr shift) and 255) - ((reference[p] ushr shift) and 255)
                        delta.toDouble() * delta
                    } / 3
                } / measured.size
                println("$label ${renderer.mode} stratified=$stratified trial=$trial spp=${shot.spp} " +
                    "last=${shot.elapsedMs}ms rms=${sqrt(meanSquare)}")
            }
        }
    }

    private data class Capture(val pixels: IntArray, val spp: Int, val elapsedMs: Long)

    private fun capture(renderer: RenderController, camera: Camera, tracer: RayTracer, deadlineMs: Long? = null): Capture {
        val frame = Frame(SIZE, SIZE)
        val last = AtomicReference<Capture>()
        val done = CountDownLatch(1)
        val failure = AtomicReference<String>()
        val start = System.nanoTime()
        renderer.renderImage(frame, tracer, camera) { progress ->
            val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
            if (deadlineMs == null && progress.isDone || deadlineMs != null && elapsed <= deadlineMs) {
                last.set(Capture(frame.pixels.copyOf(), progress.completedPasses, elapsed))
            }
            if (progress.failure != null) failure.set(progress.failure)
            if (progress.isDone || progress.failure != null) done.countDown()
        }
        // Retain only publications made by the deadline. Let all workers finish this request before mutating
        // the shared camera or starting the next measured request, so old paths cannot compete for its budget.
        check(done.await(120, TimeUnit.SECONDS)) { "sampling frame timed out" }
        check(failure.get() == null) { failure.get() }
        return checkNotNull(last.get()) { "no image by deadline" }
    }
}
