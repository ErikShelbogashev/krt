package krt.smoke

import java.io.File
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import kotlin.system.exitProcess
import krt.domain.camera.Camera
import krt.domain.render.GpuBand
import krt.domain.render.GpuFrame
import krt.domain.render.GpuSampling
import krt.domain.render.GpuTracer
import krt.domain.scene.World
import krt.domain.scene.model.DemoScene
import krt.domain.scene.model.SceneDescription
import krt.i9ns.scenefile.SceneLibrary
import krt.platform.math.Point3
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface

/**
 * The GPU smoke check (`./gradlew gpuSmoke`): every built-in scene at every benchmark size drawn on the real GPU
 * through Metal, off screen, in the steps RenderController takes: a quick look, then bands of rows under the step
 * budget, each step blended into the one before and drawn as the window draws it. It fails on a step that keeps the
 * GPU longer than [STEP_CAP_MS], on a frame that does not finish within [FRAME_CAP_MS], and on a final frame that is
 * black or has magenta pixels; a GPU that drops the commands of a stalled step leaves such a frame. Not part of
 * `check`: CI has no GPU. Run it before a PR that touches the GPU mode (ADR 0009, 0010).
 */
fun main(args: Array<String>) {
    val device = Metal.defaultDevice()
    val context = DirectContext.makeMetal(device, Metal.commandQueue(device))
    val scenes = SceneLibrary()
    val cases = DemoScene.entries.map { it.name to { scenes.load(it) } } +
        (STRESS to { scenes.load(File(STRESS_SCENE)).getOrThrow() })
    val only = args.firstOrNull()?.let { name -> cases.filter { it.first.equals(name, ignoreCase = true) } }
    val results = (only ?: cases.filter { it.first != STRESS }).flatMap { (name, load) ->
        val description = load()
        SIZES.map { (width, height) -> Smoke.run(context, name, description, width, height) }
    }
    context.close()
    val report = Smoke.report(results)
    println(report)
    File("build/reports/gpu-smoke.md").apply { parentFile.mkdirs() }.writeText(report)
    if (results.any { it.failure != null }) exitProcess(1)
}

private val SIZES = listOf(400 to 400, 800 to 600, 1024 to 1024)

/**
 * A heavy scene kept only for this check: haze through the whole room and over a thousand small lights, the scene
 * that froze the window on Metal (ADR 0010). Not in the default run; select it alone with `-Pscene=stress`.
 */
private const val STRESS = "STRESS"
private const val STRESS_SCENE = "src/gpuSmoke/scenes/deploy-stress/03-deploy-button.obj"
private const val STEP_CAP_MS = 1_000.0
private const val FRAME_CAP_MS = 60_000.0
private const val SAMPLES = 5
private const val DEPTH = 7
private const val PREVIEW_DEPTH = 3
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
    val frameMs: Double,
    val failure: String?,
)

private object Smoke {

    fun run(context: DirectContext, name: String, description: SceneDescription, width: Int, height: Int): SmokeResult {
        val scene = World(description.objects, description.look).gpuScene
        val start = requireNotNull(description.look.start)
        val view = Camera(
            start.origin, start.target, Point3(0.0, 1.0, 0.0), description.look.fov, 0.0,
            (start.target - start.origin).length(), width, height,
        ).view()
        val surface = Surface.makeRenderTarget(context, false, ImageInfo.makeN32Premul(width, height))
        val stepMs = mutableListOf<Double>()
        var failure: String? = null
        val began = System.nanoTime()
        GpuTracer().use { tracer ->
            fun draw(shader: Shader): Double {
                val drawn = System.nanoTime()
                surface.canvas.drawPaint(Paint().apply { this.shader = shader })
                context.flushAndSubmit(surface, syncCpu = true)
                return ((System.nanoTime() - drawn) / NS_PER_MS).also { stepMs += it }
            }
            val scale = GpuSampling.draftScale(width, height)
            val look = GpuFrame(
                scene, view, GpuSampling.draftSize(width, scale), GpuSampling.draftSize(height, scale),
                PREVIEW_DEPTH, 1, 0.1f, GpuBand(quick = true),
            )
            var frame = tracer.shader(look, null, scale)
            draw(frame)
            var budget = GpuSampling.StepBudget.CAUTIOUS
            var taken = 0
            var seed = 0.1f
            while (taken < SAMPLES && failure == null) {
                val samples = GpuSampling.nextStep(taken, SAMPLES, budget.samples)
                var row = 0
                while (row < height && failure == null) {
                    val rows = GpuSampling.nextRows(row, height, GpuSampling.bandsOf(budget, height))
                    seed += 0.37f
                    frame = tracer.shader(GpuFrame(scene, view, width, height, DEPTH, samples, seed, GpuBand(taken, rows)), frame)
                    val ms = draw(frame)
                    budget = budget.after(ms)
                    row = rows.last + 1
                    failure = when {
                        ms > STEP_CAP_MS -> "a step kept the GPU %.0f ms".format(ms)
                        (System.nanoTime() - began) / NS_PER_MS > FRAME_CAP_MS -> "the frame did not finish in time"
                        else -> null
                    }
                }
                taken += samples
            }
        }
        val frameMs = (System.nanoTime() - began) / NS_PER_MS
        failure = failure ?: picture(surface)
        surface.close()
        return SmokeResult(name, width, height, stepMs.size, stepMs.max(), frameMs, failure)
    }

    /** What is wrong with the frame on [surface]: magenta pixels or black, or nothing. */
    private fun picture(surface: Surface): String? {
        val bitmap = Bitmap().apply { allocN32Pixels(surface.width, surface.height) }
        surface.readPixels(bitmap, 0, 0)
        val pixels = IntArray(surface.width * surface.height) { bitmap.getColor(it % surface.width, it / surface.width) }
        bitmap.close()
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
        appendLine("| Scene | Size | Steps | Slowest step ms | Frame ms | Result |")
        appendLine("| --- | --- | --- | --- | --- | --- |")
        results.forEach {
            appendLine(
                "| ${it.scene} | ${it.width}×${it.height} | ${it.steps} | %.1f | %.0f | %s |"
                    .format(it.slowestStepMs, it.frameMs, it.failure ?: "ok"),
            )
        }
    }
}

/** The system's Metal device and a command queue of it, reached through the foreign function API. */
private object Metal {
    private val linker = Linker.nativeLinker()
    private val objc = SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", Arena.global())
    private val metal = SymbolLookup.libraryLookup("/System/Library/Frameworks/Metal.framework/Metal", Arena.global())
    private val selector = linker.downcallHandle(
        objc.find("sel_registerName").orElseThrow(), FunctionDescriptor.of(ADDRESS, ADDRESS),
    )
    private val send = linker.downcallHandle(
        objc.find("objc_msgSend").orElseThrow(), FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS),
    )

    fun defaultDevice(): Long = (
        linker.downcallHandle(metal.find("MTLCreateSystemDefaultDevice").orElseThrow(), FunctionDescriptor.of(ADDRESS))
            .invokeWithArguments() as MemorySegment
        ).address().also { check(it != 0L) { "no Metal device" } }

    fun commandQueue(device: Long): Long = message(device, "newCommandQueue")


    private fun message(receiver: Long, name: String): Long = Arena.ofConfined().use { arena ->
        val sel = selector.invokeWithArguments(arena.allocateFrom(name)) as MemorySegment
        (send.invokeWithArguments(MemorySegment.ofAddress(receiver), sel) as MemorySegment).address()
    }
}
