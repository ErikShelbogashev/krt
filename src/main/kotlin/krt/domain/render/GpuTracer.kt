package krt.domain.render

import krt.domain.camera.CameraView
import krt.domain.trace.GpuScene
import krt.platform.math.Point3
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader

/** What the GPU shader draws: scene, projection, frame size and the settings of one frame. */
class GpuFrame(
    val scene: GpuScene,
    val view: CameraView,
    val width: Int,
    val height: Int,
    val maxDepth: Int,
    val samples: Int,
    val seed: Float,
)

/**
 * Resource hiding the GPU behind Skia (ADR 0009): compiles the path tracing shader once and turns a [GpuFrame]
 * into a Skia [Shader] that draws the frame wherever Skia runs it, on the window's GPU or on the CPU raster
 * backend in tests. Closing it releases the compiled shader.
 */
class GpuTracer : AutoCloseable {

    private val effect: RuntimeEffect = RuntimeEffect.makeForShader(
        requireNotNull(GpuTracer::class.java.getResource(SHADER)) { "missing $SHADER" }.readText(),
    )

    fun shader(frame: GpuFrame): Shader {
        val builder = RuntimeShaderBuilder(effect)
        builder.child("materials", materials(frame.scene))
        builder.uniform("spheres", frame.scene.spheres)
        builder.uniform("sphereCount", frame.scene.sphereCount.toFloat())
        builder.uniform("frameSize", frame.width.toFloat(), frame.height.toFloat())
        builder.vector("camOrigin", frame.view.origin)
        builder.vector("camLowerLeft", frame.view.lowerLeftCorner)
        builder.vector("camHorizontal", frame.view.horizontal)
        builder.vector("camVertical", frame.view.vertical)
        builder.vector("camU", frame.view.u)
        builder.vector("camV", frame.view.v)
        builder.uniform("lensRadius", frame.view.lensRadius.toFloat())
        builder.uniform("maxDepth", frame.maxDepth.toFloat())
        builder.uniform("samples", frame.samples.toFloat())
        builder.uniform("seed", frame.seed)
        return builder.makeShader()
    }

    override fun close() {
        effect.close()
    }

    private fun materials(scene: GpuScene): Shader =
        Image.makeRaster(
            ImageInfo(scene.materialsWidth, scene.materialsHeight, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL, null),
            scene.materials,
            scene.materialsWidth * BYTES_PER_TEXEL,
        ).makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.DEFAULT, null)

    private fun RuntimeShaderBuilder.vector(name: String, value: Point3) =
        uniform(name, value.x.toFloat(), value.y.toFloat(), value.z.toFloat())

    private companion object {
        const val SHADER = "path_tracer.sksl"
        const val BYTES_PER_TEXEL = 4
    }
}
