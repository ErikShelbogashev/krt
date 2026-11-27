package krt.platform.skia

import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Surface

/** Runs on Skia's CPU raster backend, which draws pictures, image filters and runtime shaders like the GPU. */
class DrawnOnceShaderTest {

    /** Red grows with the x coordinate the shader is evaluated at: 0.25 per frame pixel. */
    private val ramp = RuntimeShaderBuilder(
        RuntimeEffect.makeForShader("half4 main(float2 p) { return half4(half(p.x / 4.0), 0.0, 0.0, 1.0); }"),
    ).makeShader()

    @Test
    fun `the frame is evaluated at its own size and scaled up`() {
        val shown = draw(DrawnOnceShader.of(ramp, width = 4, height = 4, scale = 2f), size = 8)
        // Shown pixel 7 lies past the last frame pixel centre (3.5): it takes that pixel's value, 3.5 / 4.
        // Evaluated at the shown size it would be 3.75 / 4.
        assertEquals(red(3.5 / 4), shown.red(7, 4))
        assertEquals(red(0.5 / 4), shown.red(0, 4))
    }

    @Test
    fun `at scale 1 the frame is drawn as the shader draws it`() {
        val shown = draw(DrawnOnceShader.of(ramp, width = 4, height = 4, scale = 1f), size = 4)
        assertEquals((0 until 4).map { red((it + 0.5) / 4) }, (0 until 4).map { shown.red(it, 2) })
    }

    @Test
    fun `nothing is drawn outside the shown frame`() {
        val shown = draw(DrawnOnceShader.of(ramp, width = 4, height = 4, scale = 2f), size = 10)
        assertEquals(0, shown.getColor(9, 9))
    }

    private fun draw(shader: org.jetbrains.skia.Shader, size: Int): Bitmap {
        val surface = Surface.makeRaster(ImageInfo.makeN32Premul(size, size))
        surface.canvas.drawPaint(Paint().apply { this.shader = shader })
        return Bitmap.makeFromImage(surface.makeImageSnapshot())
    }

    private fun Bitmap.red(x: Int, y: Int): Int = getColor(x, y) shr 16 and 0xFF

    private fun red(value: Double): Int = (value * 255).roundToInt()
}
