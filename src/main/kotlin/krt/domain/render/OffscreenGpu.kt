package krt.domain.render

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
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
import krt.platform.skia.MetalContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Matrix33
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
class OffscreenGpu internal constructor(
    initialContext: DirectContext,
    createContext: () -> DirectContext?,
    private val onStepMeasured: (Double) -> Unit,
) : AutoCloseable {
    constructor(context: DirectContext, onStepMeasured: (Double) -> Unit = {}) :
        this(context, MetalContext::create, onStepMeasured)

    private val targets = GpuTargets(initialContext, createContext)
    private val context: DirectContext get() = targets.context
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "krt-gpu").apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    @Volatile
    private var latest: GpuRequest? = null
    private val copyLock = Any()
    private val updates = FrameUpdates()
    private val tracer = GpuTracer()

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
        val current = begin(DraftGroup(sceneForFrame, frame, depth, passes), onUpdate)
        val failure = CoroutineExceptionHandler { _, cause ->
            publish(current, RenderProgress(passes, 0, 0.0, clock() - startedAt, failure = cause.toString()))
        }
        return scope.launch(failure) {
            if (latest?.accepts(current, draft = true) != true) return@launch
            val scene = sceneForFrame()
            targets.withRecovery({ isActive && latest === current }) {
                val scale = GpuSampling.draftScale(width, height, draftBudget.pixels)
                val draftMarker = targets.nextMarker()
                val look = GpuFrame(
                    scene, view, GpuSampling.draftSize(width, scale), GpuSampling.draftSize(height, scale),
                    minOf(depth, PREVIEW_DEPTH), samples = 1, seed = random.nextFloat(),
                    band = GpuBand(quick = true, marker = draftMarker),
                )
                stepBudget = GpuSampling.stepBudgetFor(scene !== budgetScene, stepBudget)
                budgetScene = scene
                draftBudget = draftBudget.after(measureStep {
                    val draft = targets.draft(look.width, look.height)
                    draw(draft, tracer.hdrShader(look))
                    readDraft(draft, frame, current, draftMarker)
                })
                publish(current, RenderProgress(passes, 0, 0.0, clock() - startedAt, isDraft = true))
                delay(REFINE_DELAY)
                if (latest !== current) return@withRecovery
                val full = GpuFrame(scene, view, width, height, depth, passes, random.nextFloat())
                refine(full, frame, current, clock, startedAt)
            }
        }
    }

    private fun CoroutineScope.refine(
        full: GpuFrame,
        frame: Frame,
        current: GpuRequest,
        clock: () -> Long,
        startedAt: Long,
    ) {
        val width = full.width
        val height = full.height
        val passes = full.samples
        val target = gpuDraw { targets.frame(width, height) }
        var published = 0L
        var taken = 0
        while (taken < passes && isActive) {
            val samples = GpuSampling.nextStep(taken, passes, stepBudget.samples)
            var row = 0
            while (row < height && isActive) {
                val rows = GpuSampling.nextRows(row, height, GpuSampling.bandsOf(stepBudget, width, height))
                row = rows.last + 1
                val marker = targets.nextMarker()
                val band = GpuBand(taken, rows, marker = marker, targetSamples = passes)
                val step = GpuFrame(full.scene, full.view, width, height, full.maxDepth, samples, full.seed, band)
                stepBudget = stepBudget.after(measureStep {
                    drawStep(target, step)
                    readBand(target, rows, marker) { copy(it, frame, rows, current) }
                })
                val done = GpuSampling.samplesDone(taken, samples, rows, height)
                val now = clock()
                // The window takes a frame at most every few milliseconds; the last step of a frame always.
                if (now - published >= PUBLISH_INTERVAL_MS || done == passes) {
                    published = now
                    publish(current, RenderProgress(passes, done, 0.0, now - startedAt))
                }
            }
            taken += samples
        }
    }

    private fun begin(group: DraftGroup, onUpdate: (RenderProgress) -> Unit): GpuRequest = synchronized(copyLock) {
        // Reuse the group only across consecutive poses. Returning to a scene starts a new group.
        GpuRequest(latest?.group?.takeIf { it == group } ?: group, onUpdate).also { latest = it }
    }

    /** Includes shader preparation, GPU execution, readback and the copy to the displayed frame. */
    private inline fun measureStep(step: () -> Unit): Double {
        val started = System.nanoTime()
        gpuDraw(step)
        return ((System.nanoTime() - started) / NS_PER_MS).also(onStepMeasured)
    }

    private fun drawStep(target: Surface, step: GpuFrame) {
        val rows = step.band.rows
        val band = IRect.makeXYWH(0, rows.first, target.width, rows.count())
        if (step.band.previousSamples == 0) return draw(target, tracer.hdrShader(step), band)
        // Only these rows contribute to their next mean. Do not replay or copy the rest of the film.
        checkNotNull(target.makeImageSnapshot(band)).use { previous ->
            previous.makeShader(Matrix33.makeTranslate(0f, rows.first.toFloat())).use { earlier ->
                draw(target, tracer.hdrShader(step, earlier), band)
            }
        }
    }

    private fun draw(
        target: Surface, shader: Shader, band: IRect = IRect.makeWH(target.width, target.height),
    ) = shader.use {
        val canvas = target.canvas
        val saved = canvas.save()
        try {
            canvas.clipRect(band.toRect())
            // Successful path samples always write alpha 1, including physically black pixels.
            canvas.clear(Color.TRANSPARENT)
            Paint().use { paint ->
                paint.shader = shader
                canvas.drawPaint(paint)
            }
        } finally {
            canvas.restoreToCount(saved)
        }
        context.flushAndSubmit(target, syncCpu = true)
    }

    /** Validate the draw at its actual resolution, then enlarge the verified pixels for display. */
    private fun readDraft(target: Surface, frame: Frame, current: GpuRequest, marker: Int) {
        val rows = 0 until frame.height
        readBand(target, 0 until target.height, marker) { bitmap ->
            if (target.width == frame.width + 1 && target.height == frame.height) {
                return copy(bitmap, frame, rows, current, draft = true)
            }
            Bitmap().use { visible ->
                check(bitmap.extractSubset(visible, IRect.makeWH(target.width - 1, target.height)))
                Bitmap().use { scaled ->
                    scaled.allocPixels(ImageInfo(frame.width, frame.height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE))
                    scalePixels(visible, scaled)
                    copy(scaled, frame, rows, current, draft = true)
                }
            }
        }
    }

    private fun copy(bitmap: Bitmap, frame: Frame, rows: IntRange, current: GpuRequest, draft: Boolean = false) {
        // Skia omits the proof column while copying into a tightly packed visible rectangle.
        val info = ImageInfo(frame.width, rows.count(), ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
        val bytes = bitmap.readPixels(info, frame.width * BYTES_PER_PIXEL, 0, 0)
            ?: error("Could not copy the GPU frame")
        val pixels = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
        synchronized(copyLock) {
            if (latest?.accepts(current, draft) == true) {
                pixels.get(frame.pixels, rows.first * frame.width, rows.count() * frame.width)
            }
        }
    }

    private fun publish(current: GpuRequest, progress: RenderProgress) = updates.offer {
        latest?.takeIf { it.accepts(current, progress.isDraft) }?.onUpdate?.invoke(progress)
    }

    /** Retire the frame and enqueue its context release before the next GPU request can begin. */
    fun invalidateFrame() {
        synchronized(copyLock) {
            if (latest == null) return
            latest = null
            executor.execute(targets::close)
        }
    }

    /** Waits for the step on the GPU thread to finish before it frees the context the step draws with. */
    override fun close() {
        synchronized(copyLock) { latest = null }
        scope.cancel()
        executor.execute {
            targets.close()
            tracer.close()
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


/** Only motion of the camera may overlap a quick look. Scene edits, resizing and render settings start a new group. */
private data class DraftGroup(val scene: () -> GpuScene, val frame: Frame, val depth: Int, val samples: Int)

private class GpuRequest(val group: DraftGroup, val onUpdate: (RenderProgress) -> Unit) {
    fun accepts(completed: GpuRequest, draft: Boolean): Boolean =
        this === completed || (draft && group === completed.group)
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
internal inline fun readBand(target: Surface, rows: IntRange, marker: Int, consume: (Bitmap) -> Unit) {
    Bitmap().use { bitmap ->
        check(bitmap.allocPixels(ImageInfo(target.width, rows.count(), ColorType.BGRA_8888, ColorAlphaType.PREMUL)))
        check(target.readPixels(bitmap, 0, rows.first)) { "Could not read the GPU frame" }
        val expected = Color.BLACK or marker
        check((0 until bitmap.height).all { bitmap.getColor(target.width - 1, it) == expected }) {
            "GPU discarded the render step"
        }
        check(bitmap.computeIsOpaque()) { "GPU returned an incomplete render band" }
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


/** The two targets belong to one context. Only the GPU worker uses or releases them. */
private class GpuTargets(initialContext: DirectContext, private val createContext: () -> DirectContext?) {
    private var active: DirectContext? = initialContext
    private var draft: Surface? = null
    private var frame: Surface? = null

    private var markerSequence = 0

    fun nextMarker(): Int {
        markerSequence = (markerSequence + 1) and RGB_MASK
        return markerSequence
    }

    val context: DirectContext
        get() = active ?: checkNotNull(createContext()) { "Could not create a Metal context" }.also { active = it }

    fun draft(width: Int, height: Int): Surface = surfaceOf(draft, width, height).also { draft = it }
    fun frame(width: Int, height: Int): Surface = surfaceOf(frame, width, height).also { frame = it }

    suspend fun withRecovery(stillCurrent: () -> Boolean, render: suspend () -> Unit) {
        var retried = false
        while (true) {
            try {
                render()
                return
            } catch (failure: GpuDrawFailure) {
                close()
                if (!stillCurrent()) return
                if (retried) throw failure
                retried = true
            }
        }
    }

    private fun surfaceOf(previous: Surface?, width: Int, height: Int): Surface =
        previous?.takeIf { it.width == width + 1 && it.height == height } ?: run {
            val info = ImageInfo(width + 1, height, ColorType.RGBA_F16, ColorAlphaType.PREMUL)
            Surface.makeRenderTarget(context, false, info).also { replacement ->
                replacement.canvas.clear(Color.TRANSPARENT)
                previous?.close()
            }
        }

    fun close() {
        draft?.close()
        draft = null
        frame?.close()
        frame = null
        active?.close()
        active = null
    }
}

private const val RGB_MASK = 0x00ffffff


private class GpuDrawFailure(cause: RuntimeException) : IllegalStateException(cause.message, cause)

@Suppress("TooGenericExceptionCaught") // Skia reports native draw and readback failures as runtime exceptions.
private inline fun <T> gpuDraw(draw: () -> T): T = try {
    draw()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: RuntimeException) {
    throw GpuDrawFailure(failure)
}
