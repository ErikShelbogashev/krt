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
import krt.domain.scene.World
import krt.platform.awt.toAWTColor

class RenderController(
    var repaintIntervalMs: Long,
    var batchSize: Int,
    private val random: Random = Random.Default,
    private val clock: () -> Long = System::currentTimeMillis,
) : AutoCloseable {
    private var renderJob: Job? = null
    private val workers = Runtime.getRuntime().availableProcessors()
    private val renderDispatcher = Executors.newFixedThreadPool(workers).asCoroutineDispatcher()
    private val repaintLock = Any()
    private var lastRepaintTime = 0L
    private var useWireframe: Boolean = false

    fun toggleWireframeMode() {
        useWireframe = !useWireframe
    }

    fun renderImage(
        frame: Frame,
        rayTracer: RayTracer,
        camera: Camera,
        onUpdate: (() -> Unit),
    ) {
        renderJob?.cancel()
        if (useWireframe) {
            renderWireframe(frame, rayTracer.world, camera, onUpdate)
        } else {
            renderRayTracedImage(frame, rayTracer, camera, onUpdate)
        }

    }

    /**
     * Workers, one per core, take batches of [batchSize] pixels in turn, so no core waits for the others.
     * Pixels go straight into the frame's pixel array. After each batch the worker that finished it
     * asks the clock whether to publish an intermediate frame; the final frame is published once all
     * workers are done.
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
        val nextBatch = AtomicInteger(0)
        val raster = frame.pixels
        renderJob = CoroutineScope(renderDispatcher).launch {
            List(workers) {
                launch {
                    while (isActive) {
                        val batch = batches.getOrNull(nextBatch.getAndIncrement()) ?: break
                        renderBatch(batch, raster, frameSeed, rayTracer, camera)
                        publishIfDue(onUpdate)
                    }
                }
            }.joinAll()
            if (isActive) SwingUtilities.invokeLater { onUpdate.invoke() }
        }
    }

    private fun CoroutineScope.renderBatch(
        batch: List<FrameSampling.Pixel>,
        raster: IntArray,
        frameSeed: Long,
        rayTracer: RayTracer,
        camera: Camera,
    ) {
        for (pixel in batch) {
            val pixelRandom = FrameSampling.pixelRandom(frameSeed, pixel, camera.imageWidth)
            val color = FrameSampling.samplePixel(pixel, camera, rayTracer, pixelRandom)
            if (!isActive) return
            val y = FrameSampling.imageY(pixel, camera.imageHeight)
            raster[y * camera.imageWidth + pixel.i] = color.toAWTColor(camera.samplesPerPixel)
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
    }

    companion object {
        fun default(): RenderController = RenderController(50L, 50)
    }
}