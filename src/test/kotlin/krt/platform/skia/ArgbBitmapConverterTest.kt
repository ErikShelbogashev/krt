package krt.platform.skia

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import kotlin.test.Test
import kotlin.test.assertEquals

class ArgbBitmapConverterTest {

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
