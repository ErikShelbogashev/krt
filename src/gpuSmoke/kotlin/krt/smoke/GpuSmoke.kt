package krt.smoke

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.system.exitProcess
import krt.domain.camera.Camera
import krt.domain.render.Frame
import krt.domain.render.OffscreenGpu
import krt.domain.render.RayTracer
import krt.domain.render.RenderController
import krt.domain.render.model.RenderMode
import krt.domain.scene.World
import krt.domain.scene.model.DemoScene
import krt.domain.scene.model.Fractal
import krt.domain.scene.model.FractalKind
import krt.domain.scene.model.SceneDescription
import krt.domain.scene.model.StartView
import krt.i9ns.scenefile.SceneLibrary
import krt.platform.math.Point3
import krt.platform.skia.MetalContext

/**
 * The GPU smoke check (`./gradlew gpuSmoke`): every built-in scene at every benchmark size drawn on the real GPU
 * through Metal, off screen, in the steps RenderController takes: a quick look, then bands of rows under the step
 * budget, each step blended into the one before and drawn as the window draws it. It fails on a step that keeps the
 * GPU longer than [STEP_CAP_MS], on a frame that does not finish within [FRAME_CAP_MS], and on a final frame that is
 * black or has magenta pixels; a GPU that drops the commands of a stalled step leaves such a frame. Not part of
 * `check`: CI has no GPU. Run it before a PR that touches the GPU mode (ADR 0009, 0010).
 * `-Pscene=fractals-deep` revisits the same 1024² frame and inspects opacity during refinement to 256 spp at depth 20.
 */
fun main(args: Array<String>) {
    // Skia reports rejected command buffers to native stderr even when readPixels and progress indicate success.
    if (args.firstOrNull() != "--metal-worker") {
        val java = File(System.getProperty("java.home"), "bin/java").path
        val worker = ProcessBuilder(
            listOf(java, "--enable-native-access=ALL-UNNAMED", "-Djava.awt.headless=true", "-cp",
                System.getProperty("java.class.path"), "krt.smoke.GpuSmokeKt", "--metal-worker") + args,
        ).redirectErrorStream(true).start()
        var rejected = false
        val lastOutput = AtomicLong(System.nanoTime())
        val output = thread(name = "Metal smoke output", isDaemon = true) {
            worker.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    println(line)
                    lastOutput.set(System.nanoTime())
                    if ("MTLCommandBufferErrorDomain" in line || "Error submitting command buffer" in line) rejected = true
                }
            }
        }
        try {
            while (!worker.waitFor(1, TimeUnit.SECONDS)) {
                val silentMs = (System.nanoTime() - lastOutput.get()) / NS_PER_MS
                check(silentMs < FRAME_CAP_MS + WORKER_GRACE_MS) { "Metal worker stopped reporting frames" }
            }
            output.join(WORKER_GRACE_MS)
            check(!output.isAlive) { "Metal worker output did not close" }
            check(worker.exitValue() == 0) { "Metal smoke worker failed" }
            check(!rejected) { "Metal rejected a command buffer during rendering" }
        } finally {
            worker.destroyForcibly()
        }
        return
    }
    val scenes = SceneLibrary()
    val cases = DemoScene.entries.map { it.name to { scenes.load(it) } } +
        (STRESS to { scenes.load(File(STRESS_SCENE)).getOrThrow() })
    val selected = args.getOrNull(1)?.let { name -> cases.filter { it.first.equals(name, ignoreCase = true) } }
        ?: cases.filter { it.first != STRESS }
    val deep = args.getOrNull(1).equals("fractals-deep", ignoreCase = true)
    require(selected.isNotEmpty() || deep) { "Unknown scene: ${args.getOrNull(1)}" }
    val context = requireNotNull(MetalContext.create()) { "Metal is required for gpuSmoke" }
    val stepMs = mutableListOf<Double>()
    val results = mutableListOf<SmokeResult>()
    OffscreenGpu(context) { stepMs.add(it) }.use { gpu ->
        RenderController(15L, 8, Random(1), offscreen = gpu).use { controller ->
            controller.mode = RenderMode.GPU
            Smoke.run(controller, stepMs, "warm-up", scenes.load(DemoScene.CLASSIC), WARM_UP, WARM_UP, SAMPLES)
            if (deep) results += Smoke.deepFractals(controller, stepMs, scenes)
            selected.forEach { (name, load) ->
                val scene = load()
                SIZES.forEach { (width, height) ->
                    results += Smoke.run(controller, stepMs, name, scene, width, height, SAMPLES)
                }
                if (name == DemoScene.MATERIALS.name) {
                    results += Smoke.run(controller, stepMs, name, scene, 2048, 2048, SAMPLES)
                    results += Smoke.run(controller, stepMs, "MATERIALS 32 spp", scene, 400, 224, 32)
                }
            }
        }
    }
    val report = Smoke.report(results)
    println(report)
    File("build/reports/gpu-smoke.md").apply { parentFile.mkdirs() }.writeText(report)
    if (results.any { it.failure != null }) exitProcess(1)
}

private val SIZES = listOf(400 to 400, 800 to 600, 1024 to 1024)
private const val WARM_UP = 16

/**
 * A heavy scene kept only for this check: haze through the whole room and over a thousand small lights, the scene
 * that froze the window on Metal (ADR 0010). Not in the default run; select it alone with `-Pscene=stress`.
 */
private const val STRESS = "STRESS"
private const val STRESS_SCENE = "src/gpuSmoke/scenes/deploy-stress/03-deploy-button.obj"
private const val STEP_CAP_MS = 1_000.0
private const val FRAME_CAP_MS = 60_000L
private const val WORKER_GRACE_MS = 15_000L
private const val SAMPLES = 5
private const val DEPTH = 7
private const val MIN_MEAN = 0.02
private const val MAGENTA = 0xFF00FF
private const val CHANNEL = 255.0
private const val NS_PER_MS = 1e6

/** One scene at one size: its slowest step, its frame time, and what failed, if anything. */
private class SmokeResult(
    val scene: String,
    val width: Int,
    val height: Int,
    val steps: Int,
    val slowestStepMs: Double,
    val medianStepMs: Double,
    val frameMs: Double,
    val failure: String?,
)

private object Smoke {

    /** Revisit a close-up of the dispersive Julia after a scene switch, reusing the same frame and Metal context. */
    fun deepFractals(
        controller: RenderController, stepMs: MutableList<Double>, scenes: SceneLibrary,
    ): List<SmokeResult> {
        val scene = scenes.load(DemoScene.FRACTALS)
        val julia = scene.objects.filterIsInstance<Fractal>().single { it.kind == FractalKind.JULIA }
        val raised = julia.translate(Point3(0.0, 0.5, 0.0))
        val close = scene.copy(
            objects = scene.objects.map { if (it === julia) raised else it },
            look = scene.look.copy(start = StartView(raised.center + Point3(-2.0, 0.55, -1.0), raised.center)),
        )
        val frame = Frame(1024, 1024)
        val first = run(controller, stepMs, "Julia close-up", close, 1024, 1024, SAMPLES, frame,
            depth = 20, aperture = close.look.aperture)
        val next = run(controller, stepMs, "Classic after Julia", scenes.load(DemoScene.CLASSIC),
            1024, 1024, SAMPLES, frame)
        val camera = Camera.default().apply {
            imageWidth = frame.width
            imageHeight = frame.height
            resetView(close.look)
        }
        val done = CountDownLatch(1)
        controller.mode = RenderMode.WIREFRAME
        controller.renderImage(frame, RayTracer(World(close.objects, close.look), 20), camera) { progress ->
            if (progress.isDone) done.countDown()
        }
        check(done.await(FRAME_CAP_MS, TimeUnit.MILLISECONDS)) { "Wireframe did not finish" }
        controller.mode = RenderMode.GPU
        return listOf(
            first, next,
            run(controller, stepMs, "Julia close-up revisited", close, 1024, 1024, 256, frame, depth = 20,
                aperture = close.look.aperture, timeout = 40 * 60_000L),
        )
    }

    fun run(
        controller: RenderController, stepMs: MutableList<Double>, name: String, description: SceneDescription,
        width: Int, height: Int, samples: Int, frame: Frame = Frame(width, height),
        depth: Int = DEPTH, aperture: Double = 0.0, timeout: Long = FRAME_CAP_MS,
    ): SmokeResult {
        val start = requireNotNull(description.look.start)
        val camera = Camera(
            start.origin, start.target, Point3(0.0, 1.0, 0.0), description.look.fov, aperture,
            (start.target - start.origin).length(), width, height,
        ).apply { samplesPerPixel = samples }
        val tracer = RayTracer(World(description.objects, description.look), depth)
        val done = CountDownLatch(1)
        var failure: String? = null
        var preview: IntArray? = null
        stepMs.clear()
        val began = System.nanoTime()
        var inspected = -2
        var lastReport = 0L
        controller.renderImage(frame, tracer, camera) { progress ->
            val pass = if (progress.isDraft) -1 else progress.completedPasses
            if (pass != inspected) {
                inspected = pass
                failure = failure ?: opacity(frame, pass)
            }
            if (timeout > FRAME_CAP_MS && progress.elapsedMs - lastReport >= 15_000L) {
                lastReport = progress.elapsedMs
                println("$name: ${progress.completedPasses}/$samples spp, ${progress.elapsedMs} ms")
            }
            if (progress.isDraft) preview = frame.pixels.copyOf()
            if (progress.isDone || progress.failure != null) {
                failure = failure ?: progress.failure
                done.countDown()
            }
        }
        check(done.await(timeout, TimeUnit.MILLISECONDS)) { "$name ${width}x$height timed out" }
        val frameMs = (System.nanoTime() - began) / NS_PER_MS
        val slowest = stepMs.maxOrNull() ?: 0.0
        failure = failure ?: when {
            slowest > STEP_CAP_MS -> "a step kept the GPU %.0f ms".format(slowest)
            preview?.contentEquals(frame.pixels) == true -> "refinement kept the quick look unchanged"
            else -> picture(frame.pixels)
        }
        val result = SmokeResult(name, width, height, stepMs.size, slowest,
            stepMs.sorted().getOrNull(stepMs.size / 2) ?: 0.0, frameMs, failure)
        println("$name ${width}×$height: ${failure ?: "ok"} (%.0f ms)".format(frameMs))
        return result
    }

    /** All published ray-traced pixels, including unlit pixels and glass, have alpha 255. */
    private fun opacity(frame: Frame, pass: Int): String? {
        val first = frame.pixels.indexOfFirst { it ushr 24 != 255 }
        if (first < 0) return null
        return "transparent pixel at (${first % frame.width},${first / frame.width}), pass $pass"
    }

    private fun picture(pixels: IntArray): String? {
        val magenta = pixels.count { it and 0xFFFFFF == MAGENTA }
        val mean = pixels.sumOf { p -> ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / (3 * CHANNEL) } /
            pixels.size
        return when {
            magenta > 0 -> "$magenta magenta pixels"
            mean < MIN_MEAN -> "black frame, mean %.3f".format(mean)
            else -> null
        }
    }

    fun report(results: List<SmokeResult>): String = buildString {
        appendLine("GPU smoke on Metal: ${results.count { it.failure == null }} of ${results.size} passed")
        appendLine()
        appendLine("| Scene | Size | Steps | Median step ms | Slowest step ms | Frame ms | Result |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- |")
        results.forEach {
            appendLine(
                "| ${it.scene} | ${it.width}×${it.height} | ${it.steps} | %.1f | %.1f | %.0f | %s |"
                    .format(it.medianStepMs, it.slowestStepMs, it.frameMs, it.failure ?: "ok"),
            )
        }
    }
}
