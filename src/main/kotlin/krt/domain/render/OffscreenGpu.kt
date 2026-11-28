package krt.domain.render

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import krt.domain.camera.Camera
import krt.domain.render.model.RenderProgress
import krt.domain.trace.GpuScene
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface

/**
 * Resource drawing GPU frames on a GPU context of the renderer's own (ADR 0010), apart from the window's: each step
 * draws into a GPU image on one thread of its own, and the next step reads that image, not a chain of pictures the
 * window would have to draw at once. The window shows the frame's pixels, read back band by band, as it shows a CPU
 * frame, so a slow step never holds up a window frame; the step's time is measured exactly, so the step budget
 * follows the GPU itself. Closing it releases the context, its surface and its thread.
 */
class OffscreenGpu(private val context: DirectContext) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "krt-gpu").apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val tracer = GpuTracer()
    private var surface: Surface? = null

    @Volatile
    private var stepBudget = GpuSampling.StepBudget()

    @Volatile
    private var draftBudget = GpuSampling.DraftBudget()
    private var budgetScene: GpuScene? = null

    /**
     * The progressive GPU frame of [camera]'s view into [frame]: a quick look, then, once the camera has rested,
     * steps of the step budget, band by band, each into the frame and reported to [onUpdate] on the UI thread.
     */
    @Suppress("LongParameterList")
    fun render(
        frame: Frame,
        rayTracer: RayTracer,
        camera: Camera,
        random: Random,
        clock: () -> Long,
        onUpdate: (RenderProgress) -> Unit,
    ): Job {
        val scene = rayTracer.world.gpuScene
        val view = camera.view()
        val passes = camera.samplesPerPixel
        val startedAt = clock()
        val (width, height) = frame.width to frame.height
        return CoroutineScope(dispatcher).launch {
            val target = surfaceOf(width, height)
            val scale = GpuSampling.draftScale(width, height, draftBudget.pixels)
            val look = GpuFrame(
                scene, view, GpuSampling.draftSize(width, scale), GpuSampling.draftSize(height, scale),
                minOf(rayTracer.maxDepth, PREVIEW_DEPTH), samples = 1, seed = random.nextFloat(),
                band = GpuBand(quick = true),
            )
            stepBudget = GpuSampling.stepBudgetFor(scene !== budgetScene, stepBudget)
            budgetScene = scene
            draftBudget = draftBudget.after(draw(target, tracer.shader(look, previous = null, scale = scale)))
            read(target, frame, 0 until height)
            publish(onUpdate, RenderProgress(passes, 0, 0.0, clock() - startedAt, isDraft = true))
            delay(REFINE_DELAY)
            var previous: Image = target.makeImageSnapshot()
            var published = 0L
            var taken = 0
            while (taken < passes && isActive) {
                val samples = GpuSampling.nextStep(taken, passes, stepBudget.samples)
                var row = 0
                while (row < height && isActive) {
                    val rows = GpuSampling.nextRows(row, height, GpuSampling.bandsOf(stepBudget, height))
                    row = rows.last + 1
                    val band = GpuBand(taken, rows)
                    val seed = random.nextFloat()
                    val step = GpuFrame(scene, view, width, height, rayTracer.maxDepth, samples, seed, band)
                    val earlier = previous.makeShader()
                    stepBudget = stepBudget.after(draw(target, tracer.shader(step, earlier)))
                    earlier.close()
                    previous.close()
                    previous = target.makeImageSnapshot()
                    read(target, frame, rows)
                    val done = GpuSampling.samplesDone(taken, samples, rows, height)
                    val now = clock()
                    // The window takes a frame at most every few milliseconds; the last step of a frame always.
                    if (now - published >= PUBLISH_INTERVAL_MS || done == passes) {
                        published = now
                        publish(onUpdate, RenderProgress(passes, done, 0.0, now - startedAt))
                    }
                }
                taken += samples
            }
            previous.close()
        }
    }

    private fun surfaceOf(width: Int, height: Int): Surface =
        surface?.takeIf { it.width == width && it.height == height } ?: run {
            surface?.close()
            Surface.makeRenderTarget(context, false, ImageInfo.makeN32Premul(width, height)).also { surface = it }
        }

    /** Draws [shader] over [target] and waits for the GPU: the time it took, in milliseconds. */
    private fun draw(target: Surface, shader: Shader): Double {
        val started = System.nanoTime()
        Paint().use { paint ->
            paint.shader = shader
            target.canvas.drawPaint(paint)
        }
        context.flushAndSubmit(target, syncCpu = true)
        shader.close()
        return (System.nanoTime() - started) / NS_PER_MS
    }

    /** Copies [rows] of [target] into [frame]'s pixels, as 0xAARRGGBB. */
    private fun read(target: Surface, frame: Frame, rows: IntRange) {
        val info = ImageInfo(frame.width, rows.count(), ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
        Bitmap().use { bitmap ->
            bitmap.allocPixels(info)
            target.readPixels(bitmap, 0, rows.first)
            val bytes = bitmap.readPixels(info, frame.width * BYTES_PER_PIXEL, 0, 0) ?: return
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
                .get(frame.pixels, rows.first * frame.width, rows.count() * frame.width)
        }
    }

    private fun publish(onUpdate: (RenderProgress) -> Unit, progress: RenderProgress) =
        SwingUtilities.invokeLater { onUpdate(progress) }

    /** Waits for the step on the GPU thread to finish before it frees the context the step draws with. */
    override fun close() {
        executor.shutdown()
        executor.awaitTermination(CLOSE_WAIT_SECONDS, TimeUnit.SECONDS)
        surface?.close()
        tracer.close()
        context.close()
    }

    private companion object {
        const val PREVIEW_DEPTH = 3
        const val BYTES_PER_PIXEL = 4
        const val NS_PER_MS = 1e6
        const val PUBLISH_INTERVAL_MS = 15L
        const val CLOSE_WAIT_SECONDS = 5L
        val REFINE_DELAY = 120.milliseconds
    }
}
