package krt.platform.skia

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/**
 * Wraps packed ARGB pixels, row by row, into Skia bitmaps for Compose: one bulk copy, no per-pixel work.
 * A little-endian ARGB int is laid out in memory as B, G, R, A, which is Skia's BGRA_8888.
 * Keeps its byte buffer between calls, so use one instance from one thread (the UI thread).
 */
class ArgbBitmapConverter {

    private var bytes = ByteArray(0)

    fun toImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap {
        val size = pixels.size * Int.SIZE_BYTES
        if (bytes.size != size) bytes = ByteArray(size)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(pixels)
        val bitmap = Bitmap()
        val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        check(bitmap.installPixels(info, bytes, width * Int.SIZE_BYTES)) { "Could not install display pixels" }
        bitmap.setImmutable()
        return bitmap.asComposeImageBitmap()
    }
}
