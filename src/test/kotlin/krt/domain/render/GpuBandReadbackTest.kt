package krt.domain.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.jetbrains.skia.Color
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

class GpuBandReadbackTest {
    @Test
    fun `one stale row in an otherwise opaque band is rejected`() {
        Surface.makeRasterN32Premul(33, 48).use { surface ->
            surface.canvas.clear(0xff123456.toInt())
            Paint().use { old ->
                old.color = 0xff123454.toInt()
                surface.canvas.drawRect(Rect.makeXYWH(32f, 20f, 1f, 1f), old)
            }
            val failure = assertFailsWith<IllegalStateException> {
                readBand(surface, 8 until 36, 0x123456) { error("Published stale pixels") }
            }
            assertEquals("GPU discarded the render step", failure.message)
        }
    }

    @Test
    fun `a transparent interior tile with a valid proof column never reaches the frame`() {
        Surface.makeRasterN32Premul(257, 160).use { surface ->
            val canvas = surface.canvas
            canvas.clear(Color.BLACK)
            Paint().use { marker ->
                marker.color = Color.RED
                canvas.drawRect(Rect.makeXYWH(256f, 0f, 1f, 160f), marker)
            }
            val rows = 32 until 128
            readBand(surface, rows, 0xff0000) { assertEquals(Color.BLACK, it.getColor(100, 40)) }
            val saved = canvas.save()
            canvas.clipRect(Rect.makeXYWH(64f, 48f, 64f, 64f))
            canvas.clear(Color.TRANSPARENT)
            canvas.restoreToCount(saved)
            val failure = assertFailsWith<IllegalStateException> {
                readBand(surface, rows, 0xff0000) { error("Damaged band reached the frame") }
            }
            assertEquals("GPU returned an incomplete render band", failure.message)
        }
    }
}
