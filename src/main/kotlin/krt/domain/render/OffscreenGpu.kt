package krt.domain.render

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import krt.domain.camera.Camera
import krt.domain.render.model.RenderProgress
import krt.domain.trace.GpuScene
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.IRect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface

/**
 * Resource drawing GPU frames on a GPU context of the renderer's own (ADR 0010), apart from the window's: each step
 * draws into a GPU image on one thread of its own, and the next step reads that image, not a chain of pictures the
 * window would have to draw at once. The window shows the frame's pixels, read back band by band, as it shows a CPU
 * frame, so a slow step never holds up a window frame; the step's time is measured exactly, so the step budget
 * follows the GPU itself. Closing it releases the context, its surface and its thread.
 */
class OffscreenGpu(
    private val context: DirectContext,
    private val onStepMeasured: (Double) -> Unit = {},
) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "krt-gpu").apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val generation = AtomicLong()
    private val copyLock = Any()
    private val updates = FrameUpdates()
    private val tracer = GpuTracer()
    private var surface: Surface? = null
    private var lastMarker = 0f
    private var draftSurface: Surface? = null
    private var lastDraftMarker = 0f

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
        val sceneForFrame = rayTracer.world.gpuSceneForFrame()
        val depth = rayTracer.maxDepth
        val view = camera.view()
        val passes = camera.samplesPerPixel
        val startedAt = clock()
        val (width, height) = frame.width to frame.height
        val current = generation.incrementAndGet()
        val failure = CoroutineExceptionHandler { _, cause ->
            publish(current, onUpdate, RenderProgress(passes, 0, 0.0, clock() - startedAt, failure = cause.toString()))
        }
        return scope.launch(failure) {
            val scene = sceneForFrame()
            val scale = GpuSampling.draftScale(width, height, draftBudget.pixels)
            val draftMarker = 1f - lastDraftMarker
            val look = GpuFrame(
                scene, view, GpuSampling.draftSize(width, scale), GpuSampling.draftSize(height, scale),
                minOf(depth, PREVIEW_DEPTH), samples = 1, seed = random.nextFloat(),
                band = GpuBand(quick = true, marker = draftMarker),
            )
            stepBudget = GpuSampling.stepBudgetFor(scene !== budgetScene, stepBudget)
            budgetScene = scene
            draftBudget = draftBudget.after(measureStep {
                val draft = surfaceOf(draftSurface, look.width, look.height).also { draftSurface = it }
                draw(draft, tracer.hdrShader(look))
                readDraft(draft, frame, current, draftMarker)
                lastDraftMarker = draftMarker
            })
            publish(current, onUpdate, RenderProgress(passes, 0, 0.0, clock() - startedAt, isDraft = true))
            delay(REFINE_DELAY)
            val target = surfaceOf(surface, width, height).also { surface = it }
            var published = 0L
            var taken = 0
            while (taken < passes && isActive) {
                val samples = GpuSampling.nextStep(taken, passes, stepBudget.samples)
                var row = 0
                while (row < height && isActive) {
                    val rows = GpuSampling.nextRows(row, height, GpuSampling.bandsOf(stepBudget, height))
                    row = rows.last + 1
                    val marker = 1f - lastMarker
                    val band = GpuBand(taken, rows, marker = marker)
                    val seed = random.nextFloat()
                    val step = GpuFrame(scene, view, width, height, depth, samples, seed, band)
                    stepBudget = stepBudget.after(measureStep {
                        drawStep(target, step)
                        read(target, frame, rows, current, marker)
                        lastMarker = marker
                    })
                    val done = GpuSampling.samplesDone(taken, samples, rows, height)
                    val now = clock()
                    // The window takes a frame at most every few milliseconds; the last step of a frame always.
                    if (now - published >= PUBLISH_INTERVAL_MS || done == passes) {
                        published = now
                        publish(current, onUpdate, RenderProgress(passes, done, 0.0, now - startedAt))
                    }
                }
                taken += samples
            }
        }
    }

    private fun surfaceOf(previous: Surface?, width: Int, height: Int): Surface =
        previous?.takeIf { it.width == width + 1 && it.height == height } ?: run {
            val info = ImageInfo(width + 1, height, ColorType.RGBA_F16, ColorAlphaType.OPAQUE)
            Surface.makeRenderTarget(context, false, info).also { replacement ->
                replacement.canvas.clear(Color.BLACK)
                previous?.close()
            }
        }

    /** Includes shader preparation, GPU execution, readback and the copy to the displayed frame. */
    private inline fun measureStep(step: () -> Unit): Double {
        val started = System.nanoTime()
        step()
        return ((System.nanoTime() - started) / NS_PER_MS).also(onStepMeasured)
    }

    private fun drawStep(target: Surface, step: GpuFrame) {
        target.makeImageSnapshot().use { previous ->
            previous.makeShader().use { earlier -> draw(target, tracer.hdrShader(step, earlier)) }
        }
    }

    private fun draw(target: Surface, shader: Shader) = shader.use {
        Paint().use { paint ->
            paint.shader = shader
            target.canvas.drawPaint(paint)
        }
        context.flushAndSubmit(target, syncCpu = true)
    }

    private fun read(target: Surface, frame: Frame, rows: IntRange, current: Long, marker: Float) {
        readBand(target, rows, marker) { bitmap -> copy(bitmap, frame, rows, current) }
    }

    /** Validate the draw at its actual resolution, then enlarge the verified pixels for display. */
    private fun readDraft(target: Surface, frame: Frame, current: Long, marker: Float) {
        val rows = 0 until frame.height
        readBand(target, 0 until target.height, marker) { bitmap ->
            if (target.width == frame.width + 1 && target.height == frame.height) {
                return copy(bitmap, frame, rows, current)
            }
            Bitmap().use { visible ->
                check(bitmap.extractSubset(visible, IRect.makeWH(target.width - 1, target.height)))
                Bitmap().use { scaled ->
                    scaled.allocPixels(ImageInfo(frame.width, frame.height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE))
                    scalePixels(visible, scaled)
                    copy(scaled, frame, rows, current)
                }
            }
        }
    }

    private fun copy(bitmap: Bitmap, frame: Frame, rows: IntRange, current: Long) {
        // Skia omits the proof column while copying into a tightly packed visible rectangle.
        val info = ImageInfo(frame.width, rows.count(), ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
        val bytes = bitmap.readPixels(info, frame.width * BYTES_PER_PIXEL, 0, 0)
            ?: error("Could not copy the GPU frame")
        val pixels = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
        synchronized(copyLock) {
            if (generation.get() == current) {
                pixels.get(frame.pixels, rows.first * frame.width, rows.count() * frame.width)
            }
        }
    }

    private fun publish(current: Long, onUpdate: (RenderProgress) -> Unit, progress: RenderProgress) =
        updates.offer { if (generation.get() == current) onUpdate(progress) }

    /** Once a new renderer takes the frame, an earlier in-flight GPU draw may no longer write into it. */
    fun invalidateFrame() {
        synchronized(copyLock) { generation.incrementAndGet() }
    }

    /** Waits for the step on the GPU thread to finish before it frees the context the step draws with. */
    override fun close() {
        invalidateFrame()
        scope.cancel()
        executor.execute {
            surface?.close()
            draftSurface?.close()
            tracer.close()
            context.close()
        }
        executor.shutdown()
        executor.awaitTermination(CLOSE_WAIT_SECONDS, TimeUnit.SECONDS)
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


/** A busy window takes the newest available progress when it can draw again. */
private class FrameUpdates {
    private var pending: (() -> Unit)? = null

    @Synchronized
    fun offer(update: () -> Unit) {
        val needsDispatch = pending == null
        pending = update
        if (needsDispatch) SwingUtilities.invokeLater(::publish)
    }

    private fun publish() {
        val latest = synchronized(this) { pending.also { pending = null } }
        latest?.invoke()
    }
}

/** Read the marker together with the picture. Skia may report success even if Metal dropped the draw. */
private inline fun readBand(target: Surface, rows: IntRange, marker: Float, consume: (Bitmap) -> Unit) {
    Bitmap().use { bitmap ->
        bitmap.allocPixels(ImageInfo(target.width, rows.count(), ColorType.BGRA_8888, ColorAlphaType.OPAQUE))
        check(target.readPixels(bitmap, 0, rows.first)) { "Could not read the GPU frame" }
        val expected = if (marker == 1f) Color.RED else Color.GREEN
        check(bitmap.getColor(target.width - 1, 0) == expected) { "GPU discarded the render step" }
        consume(bitmap)
    }
}

private fun scalePixels(visible: Bitmap, scaled: Bitmap) {
    requireNotNull(visible.peekPixels()).use { source ->
        requireNotNull(scaled.peekPixels()).use { destination ->
            check(source.scalePixels(destination, SamplingMode.LINEAR)) { "Could not scale the GPU draft" }
        }
    }
}
