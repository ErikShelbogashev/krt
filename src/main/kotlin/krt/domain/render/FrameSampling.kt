package krt.domain.render

import java.awt.Point
import kotlin.random.Random
import krt.domain.camera.Camera
import krt.domain.scene.model.Traceable
import krt.platform.math.Color
import krt.platform.math.Point3

/**
 * Pure calculations of frame rendering. All randomness and time come from parameters (ADR 0002),
 * so [RenderController] only orchestrates them and writes the results.
 */
object FrameSampling {

    data class Pixel(val i: Int, val j: Int)

    data class ScreenSegment(val start: Point, val end: Point)

    /** All pixels of a [width]×[height] frame in random order, so a partial frame covers the whole image. */
    fun pixelOrder(width: Int, height: Int, random: Random): List<Pixel> =
        (0 until height).flatMap { j -> (0 until width).map { i -> Pixel(i, j) } }.shuffled(random)

    /**
     * The order of [pixelOrder] in place, without a pixel object per pixel: [order] gets the indices `j * width + i`
     * of the pixels, shuffled with the same draws from [random] as [pixelOrder], so frames stay the same.
     */
    fun fillPixelOrder(order: IntArray, random: Random) {
        for (index in order.indices) order[index] = index
        order.shuffle(random)
    }

    /** A random generator for one pixel, derived from the frame seed so the frame does not depend on scheduling. */
    fun pixelRandom(frameSeed: Long, pixel: Pixel, width: Int): Random =
        Random(frameSeed + pixel.j.toLong() * width + pixel.i)

    /**
     * A random generator for one pixel in one pass of a progressive frame: every (pass, pixel) pair gets
     * its own seed, so the frame does not depend on scheduling. Pass 0 matches [pixelRandom].
     */
    fun passRandom(frameSeed: Long, pixel: Pixel, width: Int, height: Int, pass: Int): Random =
        Random(frameSeed + pass.toLong() * width * height + pixel.j.toLong() * width + pixel.i)

    /** One jittered sample of [pixel]. */
    fun sampleOnce(pixel: Pixel, camera: Camera, rayTracer: RayTracer, random: Random): Color {
        val u = (pixel.i.toDouble() + random.nextDouble()) / (camera.imageWidth - 1)
        val v = (pixel.j.toDouble() + random.nextDouble()) / (camera.imageHeight - 1)
        return rayTracer.color(camera.ray(u, v, random), random)
    }

    /** Sum of [Camera.samplesPerPixel] jittered samples for [pixel]; divide by the sample count when converting. */
    fun samplePixel(pixel: Pixel, camera: Camera, rayTracer: RayTracer, random: Random): Color =
        (0 until camera.samplesPerPixel).fold(Point3.ZERO) { acc, _ ->
            acc + sampleOnce(pixel, camera, rayTracer, random)
        }

    /** Image row of a pixel: the image's y axis points down, the camera's v axis points up. */
    fun imageY(pixel: Pixel, imageHeight: Int): Int = imageHeight - 1 - pixel.j

    fun shouldRepaint(now: Long, lastRepaint: Long, repaintIntervalMs: Long): Boolean =
        now - lastRepaint > repaintIntervalMs

    /** Wireframe of [objects] projected to the screen; segments with an end behind the camera are dropped. */
    fun wireframe(objects: List<Traceable>, camera: Camera): List<ScreenSegment> =
        objects.flatMap { it.linearize(WIREFRAME_DETAIL) }.mapNotNull { segment ->
            val start = camera.project(segment.start)
            val end = camera.project(segment.end)
            if (start != null && end != null) ScreenSegment(start, end) else null
        }

    private const val WIREFRAME_DETAIL = 1.0
}
