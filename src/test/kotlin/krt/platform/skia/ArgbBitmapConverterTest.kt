package krt.platform.skia

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import kotlin.test.Test
import kotlin.test.assertEquals

class ArgbBitmapConverterTest {

    @Test
    fun `published images retain their own pixels across subsequent renders`() {
        val converter = ArgbBitmapConverter()
        val pixels = IntArray(128 * 128) { 0xff123456.toInt() }
        val first = converter.toImageBitmap(pixels, 128, 128)
        pixels.fill(0xff000000.toInt())
        repeat(9) { converter.toImageBitmap(pixels, 128, 128) }
        val map = first.toPixelMap()
        for (y in 0 until 128) for (x in 0 until 128) {
            assertEquals(0xff123456.toInt(), map[x, y].toArgb())
        }
    }

    @Test
    fun `pixels keep their colours and positions`() {
        val pixels = intArrayOf(
            0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(),
            0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF123456.toInt(),
        )

        val map = ArgbBitmapConverter().toImageBitmap(pixels, 3, 2).toPixelMap()

        val readBack = List(2) { y -> List(3) { x -> map[x, y].toArgb() } }.flatten()
        assertEquals(pixels.toList(), readBack)
    }
}
