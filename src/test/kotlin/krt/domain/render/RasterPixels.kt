package krt.domain.render

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface

internal fun rasterPixels(frame: GpuFrame): IntArray {
    val info = ImageInfo.makeN32Premul(frame.width, frame.height)
    return Surface.makeRaster(info).use { surface ->
        GpuTracer().use { tracer ->
            tracer.hdrShader(frame).use { shader -> draw(surface, shader) }
        }
        surface.makeImageSnapshot().use { image ->
            Bitmap.makeFromImage(image).use { bitmap ->
                IntArray(frame.width * frame.height) { bitmap.getColor(it % frame.width, it / frame.width) }
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
