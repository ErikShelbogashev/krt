package krt.domain.render

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import krt.domain.camera.Camera
import krt.platform.awt.toAWTColor

/**
 * The quick look of a CPU frame: one sample with few bounces per block of pixels, the block filled with it, so the
 * whole frame follows a moving camera before the passes refine it. Workers take rows of blocks in turn.
 */
internal object CpuDraft {

    /** Samples of a quick look: a few milliseconds on all cores in a busy scene. */
    const val PIXELS = 16_000

    /** Bounces of a quick look. */
    const val DEPTH = 3

    /** Pass number of the quick look's random numbers, apart from the passes of the frame. */
    private const val PASS = -1

    suspend fun trace(frame: Frame, camera: Camera, rayTracer: RayTracer, frameSeed: Long, workers: Int) =
        coroutineScope {
            val scale = GpuSampling.draftScale(frame.width, frame.height, PIXELS)
            val blocks = Blocks(frame.width, frame.height, scale)
            val tracer = RayTracer(rayTracer.world, minOf(rayTracer.maxDepth, DEPTH))
            val nextRow = AtomicInteger(0)
            List(workers) {
                launch {
                    while (isActive) {
                        val row = nextRow.getAndIncrement()
                        if (row >= blocks.rows) break
                        for (column in 0 until blocks.columns) {
                            traceBlock(Block(frame, camera, tracer, frameSeed, blocks), column, row)
                        }
                    }
                }
            }.joinAll()
        }

    /** A frame cut into squares of [scale] pixels, the last ones cut short by the frame's edges. */
    private class Blocks(val width: Int, val height: Int, val scale: Int) {
        val columns = GpuSampling.draftSize(width, scale)
        val rows = GpuSampling.draftSize(height, scale)
    }

    /** What every block of one quick look shares. */
    private class Block(
        val frame: Frame,
        val camera: Camera,
        val tracer: RayTracer,
        val frameSeed: Long,
        val blocks: Blocks,
    )

    private fun traceBlock(look: Block, column: Int, row: Int) {
        val blocks = look.blocks
        val x0 = column * blocks.scale
        val y0 = row * blocks.scale
        val x = minOf(x0 + blocks.scale / 2, blocks.width - 1)
        val y = minOf(y0 + blocks.scale / 2, blocks.height - 1)
        val pixel = FrameSampling.Pixel(x, blocks.height - 1 - y)
        val random = FrameSampling.passRandom(look.frameSeed, pixel, blocks.width, blocks.height, PASS)
        val footprint = look.camera.footprint(blocks.scale.toDouble())
        val color = FrameSampling.sampleOnce(pixel, look.camera, look.tracer, random, footprint).toAWTColor(1)
        for (yy in y0 until minOf(y0 + blocks.scale, blocks.height)) {
            val rowStart = yy * blocks.width
            look.frame.pixels.fill(color, rowStart + x0, rowStart + minOf(x0 + blocks.scale, blocks.width))
        }
    }
}
