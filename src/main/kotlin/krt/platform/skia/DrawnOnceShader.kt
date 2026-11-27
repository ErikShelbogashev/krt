package krt.platform.skia

import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader

/**
 * Wraps a shader that draws a frame of `width`×`height` pixels, like the GPU path tracer (ADR 0009), so that it runs
 * once per frame pixel and once in all, however often the window repaints and however large the frame is shown.
 *
 * The frame is recorded into a picture: the shader fills the frame's rectangle and an image filter scales the
 * result by `scale`, so the shader is evaluated at the frame's size and the result is resampled, like the CPU frame.
 * The picture becomes a picture shader; Skia renders a picture shader into a texture on first use on the GPU and
 * draws that texture on later repaints while the picture lives, instead of replaying the picture.
 */
object DrawnOnceShader {

    /**
     * The objects made on the way are closed at once: Skia counts its own references, so the picture and the
     * returned shader keep what they need, and nothing waits for the garbage collector to free GPU memory.
     */
    fun of(frame: Shader, width: Int, height: Int, scale: Float): Shader =
        ImageFilter.makeMatrixTransform(Matrix33.makeScale(scale), SamplingMode.LINEAR, null).use { filter ->
            Paint().use { paint ->
                paint.shader = frame
                paint.imageFilter = filter
                PictureRecorder().use { recorder ->
                    recorder.beginRecording(Rect.makeWH(width * scale, height * scale))
                        .drawRect(Rect.makeWH(width.toFloat(), height.toFloat()), paint)
                    // No tile rectangle: the picture's own bounds are the tile; an explicit one draws nothing here.
                    recorder.finishRecordingAsPicture().use { picture ->
                        picture.makeShader(FilterTileMode.DECAL, FilterTileMode.DECAL, FilterMode.LINEAR, null, null)
                    }
                }
            }
        }
}
