package krt.app.render

import java.io.File
import krt.domain.camera.Camera
import krt.domain.render.FrameBuffer
import krt.domain.render.RayTracer
import krt.domain.render.RenderController
import krt.domain.render.model.RenderMode
import krt.domain.render.renderFrame
import krt.i9ns.png.PngFiles
import org.jetbrains.skia.Shader

class RedrawOp(
    private val renderController: RenderController,
    private val rayTracer: RayTracer,
    private val camera: Camera,
    private val frameBuffer: FrameBuffer,
) {
    operator fun invoke() = renderFrame(renderController, rayTracer, camera, frameBuffer)
}

class SetRenderModeOp(
    private val renderController: RenderController,
    private val rayTracer: RayTracer,
    private val camera: Camera,
    private val frameBuffer: FrameBuffer,
) {
    operator fun invoke(mode: RenderMode) {
        renderController.mode = mode
        renderFrame(renderController, rayTracer, camera, frameBuffer)
    }
}

class ChangeRayTracerSettingsOp(
    private val renderController: RenderController,
    private val rayTracer: RayTracer,
    private val camera: Camera,
    private val frameBuffer: FrameBuffer,
) {
    operator fun invoke(change: RayTracer.() -> Unit) {
        rayTracer.change()
        renderFrame(renderController, rayTracer, camera, frameBuffer)
    }
}

class ChangeRenderSettingsOp(
    private val renderController: RenderController,
    private val rayTracer: RayTracer,
    private val camera: Camera,
    private val frameBuffer: FrameBuffer,
) {
    operator fun invoke(change: RenderController.() -> Unit) {
        renderController.change()
        renderFrame(renderController, rayTracer, camera, frameBuffer)
    }
}

class SaveImageOp(
    private val renderController: RenderController,
    private val rayTracer: RayTracer,
    private val camera: Camera,
    private val frameBuffer: FrameBuffer,
    private val pngFiles: PngFiles,
) {
    /**
     * Writes the frame to [file]: the CPU frame as it is now, mid-render too, or in GPU mode the same view traced
     * on the CPU, once it is done.
     */
    operator fun invoke(file: File) {
        renderController.frameToSave(frameBuffer.frame, rayTracer, camera) { frame ->
            pngFiles.write(file, frame.pixels, frame.width, frame.height)
        }
    }
}

class ReportGpuFrameOp(private val renderController: RenderController) {
    /** The window showed GPU frame [shader] in a window frame of [frameMs]. */
    operator fun invoke(shader: Shader, frameMs: Double) = renderController.gpuFrameShown(shader, frameMs)
}
