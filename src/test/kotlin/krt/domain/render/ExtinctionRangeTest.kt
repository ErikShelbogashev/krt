package krt.domain.render

import kotlin.math.exp
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import krt.domain.camera.CameraView
import krt.domain.scene.World
import krt.domain.scene.model.Fog
import krt.domain.scene.model.Medium
import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Sky
import krt.domain.trace.GpuShaderSource
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface

class ExtinctionRangeTest {
    @Test
    fun `equal scattering coefficients near the float limit have equal unit weights`() {
        val main = """
            uniform float extinction;
            half4 main(float2 position) {
                return half4(half3(mixtureWeight(float3(extinction), 0.0, true) * 0.5), 1.0);
            }
        """.trimIndent()
        val source = GpuShaderSource.assemble { path ->
            if (path == GpuShaderSource.PARTS.last()) main
            else requireNotNull(javaClass.getResource(path)).readText()
        }
        RuntimeEffect.makeForShader(source).use { effect ->
            RuntimeShaderBuilder(effect).use { builder ->
                builder.uniform("extinction", 3.0e38f)
                builder.makeShader().use { shader -> assertEquals(0x808080, rgb(shader)) }
            }
        }
    }

    private fun rgb(shader: Shader): Int = Surface.makeRasterN32Premul(1, 1).use { surface ->
        Paint().use { paint ->
            paint.shader = shader
            surface.canvas.drawPaint(paint)
        }
        surface.makeImageSnapshot().use { image ->
            Bitmap.makeFromImage(image).use { it.getColor(0, 0) and 0xffffff }
        }
    }

    @Test
    fun `long paths in dilute fog and short paths in dense fog follow Beer Lambert on CPU and SkSL`() {
        checkTransmission(2048.0, 2048.0, 1.0 / 8192)
        checkTransmission(1.0, 1.0 / 16384, 8192.0)
    }

    private fun checkTransmission(radius: Double, distance: Double, extinction: Double) {
        val medium = Medium(Color(0.0, extinction, 2 * extinction), Color.ZERO)
        val white = Color.ONE * 0.25
        val world = World(listOf(Fog(Point3.ZERO, radius, medium)), SceneLook.CLASSIC.copy(sky = Sky(white, white)))
        val eye = Point3(0.0, 0.0, radius - distance)
        val direction = Point3(0.0, 0.0, 1.0 / 1024)
        val tracer = RayTracer(world, 2)
        val rng = Random(3)
        val ray = Ray(eye, direction)
        var cpu = Color.ZERO
        repeat(128) { cpu += tracer.color(ray, rng) }
        val view = CameraView(eye, eye + direction, Point3.ZERO, Point3.ZERO, Point3.ZERO, Point3.ZERO, 0.0)
        val pixels = rasterPixels(GpuFrame(world.gpuScene, view, 4, 4, 2, 8, 0.5f))
        val sums = DoubleArray(3)
        for (pixel in pixels) {
            for (channel in sums.indices) {
                val code = (pixel shr (16 - 8 * channel)) and 255
                sums[channel] += code.toDouble() * code
            }
        }
        // Escaping paths carry the same spectral ratio. The clear red channel cancels sampling noise.
        for (channel in 1..2) {
            val share = exp(-extinction * channel * distance)
            assertEquals(share, cpu[channel] / cpu.x, 1e-7)
            assertEquals(share, sums[channel] / sums[0], 0.015, "SkSL channel $channel, extinction $extinction")
        }
    }
}
