package krt.domain.render

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface

internal fun rasterPixels(
    frame: GpuFrame,
    left: Int = 0,
    top: Int = 0,
    width: Int = frame.width,
    height: Int = frame.height,
): IntArray {
    val info = ImageInfo.makeN32Premul(width, height)
    return Surface.makeRaster(info).use { surface ->
        surface.canvas.translate(-left.toFloat(), -top.toFloat())
        GpuTracer().use { tracer ->
            tracer.hdrShader(frame).use { shader -> draw(surface, shader) }
        }
        surface.makeImageSnapshot().use { image ->
            Bitmap.makeFromImage(image).use { bitmap ->
                IntArray(width * height) { bitmap.getColor(it % width, it / width) }
            }
        }
    }
}

private fun draw(surface: Surface, shader: Shader) {
    Paint().use { paint ->
        paint.shader = shader
        surface.canvas.drawPaint(paint)
    }
}
