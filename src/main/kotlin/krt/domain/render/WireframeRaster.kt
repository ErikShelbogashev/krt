package krt.domain.render

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Draws a wireframe into an ARGB pixel array in Kotlin (ADR 0008: every pixel is computed on the CPU in Kotlin,
 * not by Java2D). Segments are clipped to the frame, then drawn with Bresenham's algorithm, both ends included.
 */
object WireframeRaster {

    const val BACKGROUND: Int = 0xFF000000.toInt()
    const val LINE: Int = 0xFFFFFFFF.toInt()

    fun draw(segments: List<FrameSampling.ScreenSegment>, pixels: IntArray, width: Int, height: Int) {
        pixels.fill(BACKGROUND, 0, width * height)
        for (segment in segments) {
            val clipped = clip(segment, width, height) ?: continue
            line(clipped, pixels, width)
        }
    }

    /** Pixel ends of a line, both included. */
    internal data class Line(val startX: Int, val startY: Int, val endX: Int, val endY: Int)

    /** Pixel ends of the part of [segment] inside the frame, or null when it misses the frame. */
    internal fun clip(segment: FrameSampling.ScreenSegment, width: Int, height: Int): Line? {
        val x0 = segment.start.x.toDouble()
        val y0 = segment.start.y.toDouble()
        val dx = segment.end.x - x0
        val dy = segment.end.y - y0
        val range = doubleArrayOf(0.0, 1.0)
        val inside = clipEdge(-dx, x0, range) &&
            clipEdge(dx, width - 1 - x0, range) &&
            clipEdge(-dy, y0, range) &&
            clipEdge(dy, height - 1 - y0, range)
        if (!inside) return null
        return Line(
            (x0 + range[0] * dx).roundToInt(),
            (y0 + range[0] * dy).roundToInt(),
            (x0 + range[1] * dx).roundToInt(),
            (y0 + range[1] * dy).roundToInt(),
        )
    }

    /** One Liang–Barsky edge: keeps the part of the parameter [range] where `p * t <= q`. */
    private fun clipEdge(p: Double, q: Double, range: DoubleArray): Boolean {
        if (p == 0.0) return q >= 0
        val t = q / p
        if (p < 0) range[0] = maxOf(range[0], t) else range[1] = minOf(range[1], t)
        return range[0] <= range[1]
    }

    private fun line(ends: Line, pixels: IntArray, width: Int) {
        val endX = ends.endX
        val endY = ends.endY
        var x = ends.startX
        var y = ends.startY
        val dx = abs(endX - x)
        val dy = -abs(endY - y)
        val stepX = if (x < endX) 1 else -1
        val stepY = if (y < endY) 1 else -1
        var error = dx + dy
        while (true) {
            pixels[y * width + x] = LINE
            if (x == endX && y == endY) return
            val doubled = 2 * error
            if (doubled >= dy) {
                error += dy
                x += stepX
            }
            if (doubled <= dx) {
                error += dx
                y += stepY
            }
        }
    }
}
