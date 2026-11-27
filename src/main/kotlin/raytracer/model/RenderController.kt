package raytracer.model

import kotlinx.coroutines.*
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.concurrent.Executors
import javax.swing.SwingUtilities
import kotlin.random.Random

class RenderController(
    var repaintIntervalMs: Long,
    var batchSize: Int,
    private val random: Random = Random.Default,
    private val clock: () -> Long = System::currentTimeMillis,
) : AutoCloseable {
    private var renderJob: Job? = null
    private val renderDispatcher =
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()).asCoroutineDispatcher()
    private var lastRepaintTime = 0L
    private var useWireframe: Boolean = false

    fun toggleWireframeMode() {
        useWireframe = !useWireframe
    }

    fun renderImage(
        image: BufferedImage,
        rayTracer: RayTracer,
        camera: Camera,
        onUpdate: (() -> Unit),
    ) {
        renderJob?.cancel()
        if (useWireframe) {
            renderWireframe(image, rayTracer.world, camera, onUpdate)
        } else {
            renderRayTracedImage(image, rayTracer, camera, onUpdate)
        }

    }

    private fun renderRayTracedImage(
        image: BufferedImage,
        rayTracer: RayTracer,
        camera: Camera,
        onUpdate: (() -> Unit),
    ) {
        val pixels = FrameSampling.pixelOrder(camera.imageWidth, camera.imageHeight, random)
        val frameSeed = random.nextLong()
        renderJob = CoroutineScope(renderDispatcher).launch {
            pixels.chunked(batchSize).forEach { batch ->
                batch.map { pixel ->
                    async {
                        val pixelRandom = FrameSampling.pixelRandom(frameSeed, pixel, camera.imageWidth)
                        val color = FrameSampling.samplePixel(pixel, camera, rayTracer, pixelRandom)
                        if (isActive) {
                            image.setRGB(
                                pixel.i,
                                FrameSampling.imageY(pixel, camera.imageHeight),
                                color.toAWTColor(camera.samplesPerPixel)
                            )
                        }
                    }
                }.awaitAll()

                val currentTime = clock()
                if (FrameSampling.shouldRepaint(currentTime, lastRepaintTime, repaintIntervalMs)) {
                    SwingUtilities.invokeLater {
                        onUpdate.invoke()
                    }
                    lastRepaintTime = currentTime
                }
            }
            onUpdate.invoke()
        }
    }

    private fun renderWireframe(
        image: BufferedImage,
        world: World,
        camera: Camera,
        onUpdate: (() -> Unit),
    ) {
        val segments = FrameSampling.wireframe(world.objects, camera)
        val graphics = image.createGraphics()
        graphics.color = Color.BLACK
        graphics.fillRect(0, 0, image.width, image.height)
        graphics.color = Color.WHITE
        segments.forEach { graphics.drawLine(it.start.x, it.start.y, it.end.x, it.end.y) }
        graphics.dispose()
        onUpdate.invoke()
    }

    override fun close() {
        renderDispatcher.close()
    }

    companion object {
        fun default(): RenderController = RenderController(50L, 50)
    }
}