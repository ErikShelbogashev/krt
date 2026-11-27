package krt.domain.render

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import krt.domain.camera.Camera
import krt.domain.render.model.RenderMode
import krt.domain.render.model.RenderProgress
import krt.domain.scene.World
import krt.platform.awt.toAWTColor
import krt.platform.math.Color
import org.jetbrains.skia.Shader

class RenderController(
    var repaintIntervalMs: Long,
    var batchSize: Int,
    private val random: Random = Random.Default,
    private val clock: () -> Long = System::currentTimeMillis,
    private val gpuTracer: GpuTracer = GpuTracer(),
) : AutoCloseable {
    private var renderJob: Job? = null
    private var stillJob: Job? = null
    private val workers = Runtime.getRuntime().availableProcessors()

    /** Number of CPU workers that trace a frame, one per core. */
    val threads: Int get() = workers
    private val renderDispatcher = Executors.newFixedThreadPool(workers).asCoroutineDispatcher()
    private val repaintLock = Any()
    private var lastRepaintTime = 0L

    /** Which renderer draws the next frame; in GPU mode (ADR 0009) a Skia shader draws it instead of the workers. */
    @Volatile
    var mode: RenderMode = RenderMode.CPU

    /**
     * Draws the frame: the wireframe or the CPU path tracer into [frame], reporting progress to [onUpdate],
     * or, in GPU mode, hands a shader that draws the frame to [onGpuFrame].
     */
    fun renderImage(
        frame: Frame,
        rayTracer: RayTracer,
        camera: Camera,
        onGpuFrame: ((Shader, RenderProgress) -> Unit) = { _, _ -> },
        onUpdate: ((RenderProgress) -> Unit),
    ) {
        renderJob?.cancel()
        when (mode) {
            RenderMode.WIREFRAME -> renderWireframe(frame, rayTracer.world, camera, onUpdate)
            RenderMode.GPU -> renderGpuImage(rayTracer, camera, onGpuFrame)
            RenderMode.CPU -> renderJob = traceFrame(frame, rayTracer, camera, onUpdate)
        }
    }

    /**
     * The frame to save, handed to [onReady] on the UI thread: the [shown] frame itself, or in GPU mode, whose frame
     * stays on the GPU (ADR 0009), the same view traced on the CPU workers with the same samples, in a frame of its
     * own, while the GPU frame stays on screen.
     */
    fun frameToSave(shown: Frame, rayTracer: RayTracer, camera: Camera, onReady: (Frame) -> Unit) {
        if (mode != RenderMode.GPU) return onReady(shown)
        val frame = Frame(camera.imageWidth, camera.imageHeight)
        stillJob?.cancel()
        stillJob = traceFrame(frame, rayTracer, camera) { progress -> if (progress.isDone) onReady(frame) }
    }

    /**
     * Progressive GPU frame (ADR 0009). First a quick look: one sample per pixel at a resolution of at most
     * [GpuSampling.draftScale]'s budget, with few bounces, shown at once. The GPU draws a step inside a window
     * repaint, so while the camera moves every repaint, only quick looks keep the window fluid. When the camera has
     * rested for [REFINE_DELAY_MS], steps of [GpuSampling.steps] samples at full resolution and depth follow,
     * each blended into the frame of the steps before, one per [repaintIntervalMs]. A newer frame cancels them all.
     */
    private fun renderGpuImage(rayTracer: RayTracer, camera: Camera, onGpuFrame: (Shader, RenderProgress) -> Unit) {
        val scene = rayTracer.world.gpuScene
        val view = camera.view()
        val passes = camera.samplesPerPixel
        val startedAt = clock()
        val width = camera.imageWidth
        val height = camera.imageHeight
        renderJob = CoroutineScope(renderDispatcher).launch {
            val scale = GpuSampling.draftScale(width, height)
            val look = GpuFrame(
                scene, view, GpuSampling.draftSize(width, scale), GpuSampling.draftSize(height, scale),
                minOf(rayTracer.maxDepth, PREVIEW_DEPTH), samples = 1, seed = random.nextFloat(),
            )
            val preview = gpuTracer.shader(look, previous = null, scale = scale)
            val previewProgress = RenderProgress(passes, 0, 0.0, clock() - startedAt)
            SwingUtilities.invokeLater { onGpuFrame(preview, previewProgress) }
            delay(REFINE_DELAY_MS)
            var frame: Shader? = null
            var taken = 0
            for (samples in GpuSampling.steps(passes)) {
                if (!isActive) break
                val step = GpuFrame(
                    scene, view, camera.imageWidth, camera.imageHeight, rayTracer.maxDepth, samples, random.nextFloat(),
                    previousSamples = taken,
                )
                val shader = gpuTracer.shader(step, frame)
                frame = shader
                taken += samples
                val progress = RenderProgress(passes, taken, 0.0, clock() - startedAt)
                SwingUtilities.invokeLater { onGpuFrame(shader, progress) }
                delay(repaintIntervalMs)
            }
        }
    }

    /**
     * Progressive frame: [Camera.samplesPerPixel] passes of one sample per pixel each, so a noisy but complete
     * frame appears after the first pass and sharpens with every next one. Every pixel shows the average
     * of the samples taken so far; after the last pass the frame has the full sample count.
     *
     * Within a pass, workers, one per core, take batches of [batchSize] pixels in turn, so no core waits for
     * the others. Pixels go straight into the frame's pixel array. After each batch the worker that finished it
     * asks the clock whether to publish an intermediate frame; the final frame is published once all passes
     * are done. Every update carries the progress of the frame at the moment it was published.
     * The caller's thread only reads the camera's size; the pixel order and sums are set up in the job.
     */
    private fun traceFrame(
        frame: Frame,
        rayTracer: RayTracer,
        camera: Camera,
        onUpdate: ((RenderProgress) -> Unit),
    ): Job {
        val width = camera.imageWidth
        val height = camera.imageHeight
        val passes = camera.samplesPerPixel
        val batchPixels = batchSize
        val startedAt = clock()
        val scratch = scratchFor(width * height)
        val job = CoroutineScope(renderDispatcher).launch {
            // Setting up a frame of a million pixels takes a while: off the UI thread, which a dragged camera
            // asks for a new frame every repaint.
            FrameSampling.fillPixelOrder(scratch.order, random)
            val work = FrameWork(frame, rayTracer, camera, scratch, width, batchPixels, random.nextLong())
            for (pass in 0 until passes) {
                if (!isActive) break
                val nextBatch = AtomicInteger(0)
                val doneBatches = AtomicInteger(0)
                List(workers) {
                    launch {
                        while (isActive) {
                            val batch = work.batch(nextBatch.getAndIncrement()) ?: break
                            renderBatch(batch, Pass(pass, work.frameSeed), work)
                            val passFraction = doneBatches.incrementAndGet().toDouble() / work.batchCount
                            publishIfDue(onUpdate) { now ->
                                RenderProgress(passes, pass, passFraction, now - startedAt)
                            }
                        }
                    }
                }.joinAll()
            }
            if (!isActive) return@launch
            val progress = RenderProgress.finished(passes, clock() - startedAt)
            SwingUtilities.invokeLater { onUpdate(progress) }
        }
        // Back to the pool only once the job and all its workers are done with it, cancelled or not.
        job.invokeOnCompletion { releaseScratch(scratch) }
        return job
    }

    /**
     * Pixel order and sample sums of one frame, about 28 bytes per pixel. A dragged camera starts a frame every
     * repaint; taking them from a pool instead of allocating them spares the collector tens of megabytes a frame.
     */
    private class Scratch(val pixelCount: Int) {
        val order = IntArray(pixelCount)
        val sums = DoubleArray(pixelCount * CHANNELS)
    }

    private val scratchPool = ConcurrentLinkedQueue<Scratch>()

    private fun scratchFor(pixelCount: Int): Scratch =
        generateSequence { scratchPool.poll() }.firstOrNull { it.pixelCount == pixelCount } ?: Scratch(pixelCount)

    private fun releaseScratch(scratch: Scratch) {
        scratch.sums.fill(0.0)
        scratchPool.offer(scratch)
    }

    /** Pixels `order[from until to]`, each an index `j * width + i` of pixel (i, j). */
    private class Pixels(val order: IntArray, val from: Int, val to: Int, val width: Int)

    /** What the workers of one CPU frame share: where they write, what they trace, and its batches. */
    @Suppress("LongParameterList")
    private class FrameWork(
        val frame: Frame,
        val rayTracer: RayTracer,
        val camera: Camera,
        scratch: Scratch,
        private val width: Int,
        private val batchPixels: Int,
        val frameSeed: Long,
    ) {
        private val order = scratch.order
        val accumulated = Accumulator(scratch.sums)
        val batchCount = (order.size + batchPixels - 1) / batchPixels

        /** Batch number [index], or null past the last one. */
        fun batch(index: Int): Pixels? {
            if (index >= batchCount) return null
            val from = index * batchPixels
            return Pixels(order, from, minOf(from + batchPixels, order.size), width)
        }
    }

    private class Pass(val index: Int, val frameSeed: Long)

    /** Sum of the samples taken so far, three doubles (r, g, b) per pixel, in [sums]. */
    private class Accumulator(private val sums: DoubleArray) {

        fun add(index: Int, color: Color): Color {
            val at = index * CHANNELS
            sums[at] += color.x
            sums[at + 1] += color.y
            sums[at + 2] += color.z
            return Color(sums[at], sums[at + 1], sums[at + 2])
        }

    }

    private fun CoroutineScope.renderBatch(batch: Pixels, pass: Pass, work: FrameWork) {
        val camera = work.camera
        for (k in batch.from until batch.to) {
            val pixel = FrameSampling.Pixel(batch.order[k] % batch.width, batch.order[k] / batch.width)
            val random =
                FrameSampling.passRandom(pass.frameSeed, pixel, camera.imageWidth, camera.imageHeight, pass.index)
            val color = FrameSampling.sampleOnce(pixel, camera, work.rayTracer, random)
            if (!isActive) return
            val index = FrameSampling.imageY(pixel, camera.imageHeight) * camera.imageWidth + pixel.i
            work.frame.pixels[index] = work.accumulated.add(index, color).toAWTColor(pass.index + 1)
        }
    }

    /** One worker at a time reads the clock, so the repaint decision stays consistent across workers. */
    private fun publishIfDue(onUpdate: (RenderProgress) -> Unit, progressAt: (Long) -> RenderProgress) {
        val currentTime = synchronized(repaintLock) {
            val currentTime = clock()
            if (!FrameSampling.shouldRepaint(currentTime, lastRepaintTime, repaintIntervalMs)) return
            lastRepaintTime = currentTime
            currentTime
        }
        val progress = progressAt(currentTime)
        SwingUtilities.invokeLater { onUpdate(progress) }
    }

    private fun renderWireframe(
        frame: Frame,
        world: World,
        camera: Camera,
        onUpdate: ((RenderProgress) -> Unit),
    ) {
        val startedAt = clock()
        val segments = FrameSampling.wireframe(world.objects, camera)
        WireframeRaster.draw(segments, frame.pixels, frame.width, frame.height)
        onUpdate(RenderProgress.finished(1, clock() - startedAt))
    }

    override fun close() {
        renderJob?.cancel()
        stillJob?.cancel()
        renderDispatcher.close()
        gpuTracer.close()
    }

    companion object {
        /** Bounces of the quick first look of a GPU frame. */
        private const val PREVIEW_DEPTH = 3

        /** How long the camera rests before a GPU frame is refined at full resolution. */
        private const val REFINE_DELAY_MS = 120L

        /** Sums per pixel: r, g and b. */
        private const val CHANNELS = 3

        fun default(): RenderController = RenderController(50L, 50)
    }
}