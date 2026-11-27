package krt.domain.render

import java.awt.Color
import java.awt.Point
import java.awt.image.BufferedImage
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import krt.domain.render.FrameSampling.ScreenSegment

class WireframeRasterTest {

    @Test
    fun `segments inside the frame match Java2D drawLine pixel for pixel`() {
        val random = Random(5)
        repeat(SEGMENT_SETS) {
            val segments = List(SEGMENTS) {
                ScreenSegment(
                    Point(random.nextInt(WIDTH), random.nextInt(HEIGHT)),
                    Point(random.nextInt(WIDTH), random.nextInt(HEIGHT)),
                )
            }
            assertContentEquals(java2d(segments), kotlin(segments))
        }
    }

    @Test
    fun `both ends of a segment are drawn`() {
        val pixels = kotlin(listOf(ScreenSegment(Point(2, 3), Point(9, 3))))
        assertEquals(WireframeRaster.LINE, pixels[3 * WIDTH + 2])
        assertEquals(WireframeRaster.LINE, pixels[3 * WIDTH + 9])
        assertEquals(WireframeRaster.BACKGROUND, pixels[3 * WIDTH + 10])
    }

    @Test
    fun `a segment far outside the frame is clipped, not walked`() {
        val ends = WireframeRaster.clip(ScreenSegment(Point(-1_000_000, 5), Point(1_000_000, 5)), WIDTH, HEIGHT)
        assertEquals(WireframeRaster.Line(0, 5, WIDTH - 1, 5), ends)
    }

    @Test
    fun `a segment that misses the frame draws nothing`() {
        assertNull(WireframeRaster.clip(ScreenSegment(Point(-10, -10), Point(-1, HEIGHT + 5)), WIDTH, HEIGHT))
        val pixels = kotlin(listOf(ScreenSegment(Point(-10, -1), Point(50, -1))))
        assertEquals(setOf(WireframeRaster.BACKGROUND), pixels.toSet())
    }

    private fun kotlin(segments: List<ScreenSegment>): IntArray =
        IntArray(WIDTH * HEIGHT).also { WireframeRaster.draw(segments, it, WIDTH, HEIGHT) }

    private fun java2d(segments: List<ScreenSegment>): IntArray {
        val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.color = Color.BLACK
        graphics.fillRect(0, 0, WIDTH, HEIGHT)
        graphics.color = Color.WHITE
        segments.forEach { graphics.drawLine(it.start.x, it.start.y, it.end.x, it.end.y) }
        graphics.dispose()
        return image.getRGB(0, 0, WIDTH, HEIGHT, null, 0, WIDTH)
    }

    private companion object {
        const val WIDTH = 64
        const val HEIGHT = 48
        const val SEGMENTS = 20
        const val SEGMENT_SETS = 200
    }
}
