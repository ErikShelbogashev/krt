package krt.domain.render

import java.util.concurrent.CancellationException
import krt.domain.camera.CameraView
import krt.domain.trace.GpuScene
import krt.domain.trace.GpuShaderSource
import krt.platform.math.Point3
import krt.platform.skia.DrawnOnceShader
import org.jetbrains.skia.Color4f
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
 * and which rows it takes them in, with the samples per pixel that earlier steps took there ([band]).
 */
class GpuFrame(
    val scene: GpuScene,
    val view: CameraView,
    val width: Int,
    val height: Int,
    val maxDepth: Int,
    val samples: Int,
    val seed: Float,
    val band: GpuBand = GpuBand(),
)

/**
 * The rows, from the top of the window, a GPU step samples, and the [previousSamples] per pixel that earlier steps
 * took in them; the other rows keep the frame of the earlier steps as it is. A [quick] look of a frame skips light
 * sampling and media, which can cost a heavy scene many times the rest (ADR 0010).
 */
data class GpuBand(
    val previousSamples: Int = 0,
    val rows: IntRange = 0 until Int.MAX_VALUE,
    val quick: Boolean = false,
    val marker: Int = 0,
) {
    /** The lights a step of [scene] samples: none in a quick look. */
    fun sampledLights(scene: GpuScene): Int = if (quick) 0 else scene.sampledLights

    /** 1 for a quick look, which the shader draws without media, else 0. */
    val quickLook: Float get() = if (quick) 1f else 0f
}

/**
 * Resource hiding the GPU behind Skia (ADR 0009): compiles the path tracing shader once and turns a [GpuFrame]
 * into a Skia [Shader] that draws the frame wherever Skia runs it, on the window's GPU or on the CPU raster
 * backend in tests. Closing it releases the compiled shader.
 */
class GpuTracer : AutoCloseable {

    private val effect: RuntimeEffect = RuntimeEffect.makeForShader(GpuShaderSource.assemble(::part))

    private val windowDisplay = RuntimeEffect.makeForShader(GpuShaderSource.windowDisplay(::part))

    /**
     * The window fallback caches pictures in eight-bit textures. Each pixel keeps its shared exponent in alpha.
     * The window owns each display shader it receives; the job owns the encoded texture until the next step.
     */
    inner class WindowSteps : AutoCloseable {
        private var previous: Shader? = null

        fun next(frame: GpuFrame, scale: Int = 1): Shader {
            val texture = step(frame, previous, packed = true).use { shader ->
                DrawnOnceShader.of(shader, frame.width, frame.height, scale.toFloat())
            }
            previous?.close()
            previous = texture
            return display(texture)
        }

        override fun close() {
            previous?.close()
        }
    }

    @Synchronized
    private fun display(texture: Shader): Shader = builder(windowDisplay).use { builder ->
        builder.child("frame", texture)
        builder.makeShader()
    }

    /**
     * The frame after one more step: [previous] (the frame of the earlier steps, null before the first) with
     * [GpuFrame.samples] more samples per pixel. The step runs once on the GPU, at the frame's size, when first drawn;
     * a coarse frame, [scale] times smaller each way, is shown [scale] times larger.
     */
    fun shader(frame: GpuFrame, previous: Shader? = null, scale: Int = 1): Shader =
        hdrShader(frame, previous).use { step ->
            DrawnOnceShader.of(step, frame.width, frame.height, scale.toFloat())
        }

    /** Draw this step directly to an RGBA_F16 target to preserve radiance above display white between passes. */
    fun hdrShader(frame: GpuFrame, previous: Shader? = null): Shader = step(frame, previous, packed = false)

    @Synchronized
    private fun step(frame: GpuFrame, previous: Shader?, packed: Boolean): Shader = builder(effect).use { builder ->
        builder.uniform("packedWindow", if (packed) 1f else 0f)
        builder.child("previous", previous ?: noFrame)
        builder.uniform("previousSamples", frame.band.previousSamples.toFloat())
        val stamp = Color4f(frame.band.marker)
        builder.uniform("stepMarker", stamp.r, stamp.g, stamp.b)
        builder.uniform("bandStart", frame.band.rows.first.toFloat())
        builder.uniform("bandEnd", frame.band.rows.last.toFloat() + 1)
        builder.child("scene", sceneTexture(frame.scene))
        builder.uniform("nodeCount", frame.scene.nodeCount.toFloat())
        builder.uniform("primitiveStart", frame.scene.primitiveStart.toFloat())
        builder.uniform("materialStart", frame.scene.materialStart.toFloat())
        builder.uniform("lightStart", frame.scene.sections.lightStart.toFloat())
        builder.uniform("lightCount", frame.band.sampledLights(frame.scene).toFloat())
        builder.uniform("quickLook", frame.band.quickLook)
        builder.uniform("memberStart", frame.scene.sections.memberStart.toFloat())
        builder.uniform("normalStart", frame.scene.sections.normalStart.toFloat())
        builder.uniform("mediaStart", frame.scene.sections.mediaStart.toFloat())
        builder.uniform("cameraMedium", frame.scene.mediumAt(frame.view.origin).toFloat())
        builder.uniform("motionBlur", frame.scene.motionBlur)
        builder.uniform("smoothNormals", frame.scene.smoothNormals)
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
        builder.makeShader()
    }

    /** A cancelled window job may acquire our lock just after the composition root has closed the tracer. */
    private fun builder(effect: RuntimeEffect): RuntimeShaderBuilder {
        if (effect.isClosed) throw CancellationException("GPU tracer closed")
        return RuntimeShaderBuilder(effect)
    }

    private val noFrame: Shader = Shader.makeColor(BLACK)

    @Synchronized
    override fun close() {
        sceneTexture?.second?.close()
        sceneTexture = null
        windowDisplay.close()
        noFrame.close()
        effect.close()
    }

    /** The texture of the scene drawn last, so a new frame of the same scene does not upload it again. */
    private var sceneTexture: Pair<GpuScene, Shader>? = null

    private fun sceneTexture(scene: GpuScene): Shader =
        sceneTexture?.takeIf { it.first === scene }?.second
            ?: makeSceneTexture(scene).also {
                sceneTexture?.second?.close()
                sceneTexture = scene to it
            }

    private fun makeSceneTexture(scene: GpuScene): Shader =
        Image.makeRaster(
            ImageInfo(scene.width, scene.height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL, null),
            scene.data,
            scene.width * BYTES_PER_TEXEL,
        ).use { it.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.DEFAULT, null) }

    private fun part(path: String): String =
        requireNotNull(GpuTracer::class.java.getResource(path)) { "missing $path" }.readText()

    private fun RuntimeShaderBuilder.vector(name: String, value: Point3) =
        uniform(name, value.x.toFloat(), value.y.toFloat(), value.z.toFloat())

    private companion object {
        const val BYTES_PER_TEXEL = 4
        const val BLACK = 0xFF000000.toInt()
    }
}
