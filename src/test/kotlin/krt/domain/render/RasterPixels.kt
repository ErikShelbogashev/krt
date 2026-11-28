package krt.domain.render

import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
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

/** Two offscreen-style passes with a half-float snapshot between them. */
internal fun rasterPixels(first: GpuFrame, second: GpuFrame): IntArray {
    val info = ImageInfo(first.width, first.height, ColorType.RGBA_F16, ColorAlphaType.PREMUL)
    return Surface.makeRaster(info).use { target ->
        GpuTracer().use { tracer ->
            tracer.hdrShader(first).use { draw(target, it) }
            drawNext(target, tracer, second)
        }
        Bitmap().use { bitmap ->
            check(bitmap.allocPixels(ImageInfo.makeN32Premul(first.width, first.height)))
            check(target.readPixels(bitmap, 0, 0))
            IntArray(first.width * first.height) { bitmap.getColor(it % first.width, it / first.width) }
        }
    }
}

private fun drawNext(target: Surface, tracer: GpuTracer, frame: GpuFrame) {
    target.makeImageSnapshot().use { image ->
        image.makeShader().use { previous -> tracer.hdrShader(frame, previous).use { draw(target, it) } }
    }
}

private fun draw(surface: Surface, shader: Shader) {
    Paint().use { paint ->
        paint.shader = shader
        surface.canvas.drawPaint(paint)
    }
}
