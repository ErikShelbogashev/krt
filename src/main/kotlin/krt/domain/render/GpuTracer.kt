package krt.domain.render

import krt.domain.camera.CameraView
import krt.domain.trace.GpuScene
import krt.platform.math.Point3
import krt.platform.skia.DrawnOnceShader
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader

/**
 * What the GPU shader draws in one step of a frame: scene, projection, frame size, depth, the [samples] of this step
 * and the [previousSamples] per pixel that earlier steps of the same frame took.
 */
class GpuFrame(
    val scene: GpuScene,
    val view: CameraView,
    val width: Int,
    val height: Int,
    val maxDepth: Int,
    val samples: Int,
    val seed: Float,
    val previousSamples: Int = 0,
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

    /**
     * The frame after one more step: [previous] (the frame of the earlier steps, null before the first) with
     * [GpuFrame.samples] more samples per pixel. The step runs once on the GPU, at the frame's size, when first drawn;
     * a coarse frame, [scale] times smaller each way, is shown [scale] times larger.
     */
    fun shader(frame: GpuFrame, previous: Shader? = null, scale: Int = 1): Shader {
        val builder = RuntimeShaderBuilder(effect)
        builder.child("previous", previous ?: noFrame)
        builder.uniform("previousSamples", frame.previousSamples.toFloat())
        builder.child("scene", sceneTexture(frame.scene))
        builder.uniform("nodeCount", frame.scene.nodeCount.toFloat())
        builder.uniform("primitiveStart", frame.scene.primitiveStart.toFloat())
        builder.uniform("materialStart", frame.scene.materialStart.toFloat())
        builder.vector("skyHorizon", frame.scene.sky.horizon)
        builder.vector("skyZenith", frame.scene.sky.zenith)
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
        // The drawn-once shader holds its own reference to the step's shader, so ours goes at once.
        return builder.makeShader().use { step ->
            builder.close()
            DrawnOnceShader.of(step, frame.width, frame.height, scale.toFloat())
        }
    }

    private val noFrame: Shader = Shader.makeColor(BLACK)

    override fun close() {
        noFrame.close()
        effect.close()
    }

    /** The texture of the scene drawn last, so a new frame of the same scene does not upload it again. */
    private var sceneTexture: Pair<GpuScene, Shader>? = null

    @Synchronized
    private fun sceneTexture(scene: GpuScene): Shader =
        sceneTexture?.takeIf { it.first === scene }?.second
            ?: makeSceneTexture(scene).also { sceneTexture = scene to it }

    private fun makeSceneTexture(scene: GpuScene): Shader =
        Image.makeRaster(
            ImageInfo(scene.width, scene.height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL, null),
            scene.data,
            scene.width * BYTES_PER_TEXEL,
        ).makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.DEFAULT, null)

    private fun RuntimeShaderBuilder.vector(name: String, value: Point3) =
        uniform(name, value.x.toFloat(), value.y.toFloat(), value.z.toFloat())

    private companion object {
        const val SHADER = "path_tracer.sksl"
        const val BYTES_PER_TEXEL = 4
        const val BLACK = 0xFF000000.toInt()
    }
}
