package krt.domain.render

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import krt.domain.camera.Camera
import krt.domain.render.model.RenderMode
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
    private val workers = Runtime.getRuntime().availableProcessors()
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
        onGpuFrame: ((Shader) -> Unit) = {},
        onUpdate: (() -> Unit),
    ) {
        renderJob?.cancel()
        when (mode) {
            RenderMode.WIREFRAME -> renderWireframe(frame, rayTracer.world, camera, onUpdate)
            RenderMode.GPU -> renderGpuImage(rayTracer, camera, onGpuFrame)
            RenderMode.CPU -> renderRayTracedImage(frame, rayTracer, camera, onUpdate)
        }
    }

    private fun renderGpuImage(rayTracer: RayTracer, camera: Camera, onGpuFrame: (Shader) -> Unit) {
        val gpuFrame = GpuFrame(
            scene = rayTracer.world.gpuScene,
            view = camera.view(),
            width = camera.imageWidth,
            height = camera.imageHeight,
            maxDepth = rayTracer.maxDepth,
            samples = camera.samplesPerPixel,
            seed = random.nextFloat(),
        )
        onGpuFrame(gpuTracer.shader(gpuFrame))
    }

    /**
     * Progressive frame: [Camera.samplesPerPixel] passes of one sample per pixel each, so a noisy but complete
     * frame appears after the first pass and sharpens with every next one. Every pixel shows the average
     * of the samples taken so far; after the last pass the frame has the full sample count.
     *
     * Within a pass, workers, one per core, take batches of [batchSize] pixels in turn, so no core waits for
     * the others. Pixels go straight into the frame's pixel array. After each batch the worker that finished it
     * asks the clock whether to publish an intermediate frame; the final frame is published once all passes
     * are done.
     */
    private fun renderRayTracedImage(
        frame: Frame,
        rayTracer: RayTracer,
        camera: Camera,
        onUpdate: (() -> Unit),
    ) {
        val pixels = FrameSampling.pixelOrder(camera.imageWidth, camera.imageHeight, random)
        val frameSeed = random.nextLong()
        val batches = pixels.chunked(batchSize)
        val accumulated = Accumulator(camera.imageWidth * camera.imageHeight)
        renderJob = CoroutineScope(renderDispatcher).launch {
            for (pass in 0 until camera.samplesPerPixel) {
                if (!isActive) break
                val nextBatch = AtomicInteger(0)
                List(workers) {
                    launch {
                        while (isActive) {
                            val batch = batches.getOrNull(nextBatch.getAndIncrement()) ?: break
                            renderBatch(batch, Pass(pass, frameSeed), frame, accumulated, rayTracer, camera)
                            publishIfDue(onUpdate)
                        }
                    }
                }.joinAll()
            }
            if (isActive) SwingUtilities.invokeLater { onUpdate.invoke() }
        }
    }

    private class Pass(val index: Int, val frameSeed: Long)

    /** Sum of the samples taken so far, three doubles (r, g, b) per pixel. */
    private class Accumulator(pixelCount: Int) {
        private val sums = DoubleArray(pixelCount * CHANNELS)

        fun add(index: Int, color: Color): Color {
            val at = index * CHANNELS
            sums[at] += color.x
            sums[at + 1] += color.y
            sums[at + 2] += color.z
            return Color(sums[at], sums[at + 1], sums[at + 2])
        }

        private companion object {
            const val CHANNELS = 3
        }
    }

    @Suppress("LongParameterList")
    private fun CoroutineScope.renderBatch(
        batch: List<FrameSampling.Pixel>,
        pass: Pass,
        frame: Frame,
        accumulated: Accumulator,
        rayTracer: RayTracer,
        camera: Camera,
    ) {
        for (pixel in batch) {
            val random =
                FrameSampling.passRandom(pass.frameSeed, pixel, camera.imageWidth, camera.imageHeight, pass.index)
            val color = FrameSampling.sampleOnce(pixel, camera, rayTracer, random)
            if (!isActive) return
            val index = FrameSampling.imageY(pixel, camera.imageHeight) * camera.imageWidth + pixel.i
            frame.pixels[index] = accumulated.add(index, color).toAWTColor(pass.index + 1)
        }
    }

    /** One worker at a time reads the clock, so the repaint decision stays consistent across workers. */
    private fun publishIfDue(onUpdate: () -> Unit) {
        synchronized(repaintLock) {
            val currentTime = clock()
            if (!FrameSampling.shouldRepaint(currentTime, lastRepaintTime, repaintIntervalMs)) return
            lastRepaintTime = currentTime
        }
        SwingUtilities.invokeLater { onUpdate.invoke() }
    }

    private fun renderWireframe(
        frame: Frame,
        world: World,
        camera: Camera,
        onUpdate: (() -> Unit),
    ) {
        val segments = FrameSampling.wireframe(world.objects, camera)
        WireframeRaster.draw(segments, frame.pixels, frame.width, frame.height)
        onUpdate.invoke()
    }

    override fun close() {
        renderJob?.cancel()
        renderDispatcher.close()
        gpuTracer.close()
    }

    companion object {
        fun default(): RenderController = RenderController(50L, 50)
    }
}