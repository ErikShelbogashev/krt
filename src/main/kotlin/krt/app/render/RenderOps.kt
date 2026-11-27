package krt.app.render

import java.io.File
import krt.domain.camera.Camera
import krt.domain.render.FrameBuffer
import krt.domain.render.RayTracer
import krt.domain.render.RenderController
import krt.domain.render.model.RenderMode
import krt.domain.render.renderFrame
import krt.i9ns.png.PngFiles

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
    private val frameBuffer: FrameBuffer,
    private val pngFiles: PngFiles,
) {
    /** Writes the CPU frame as it is now, mid-render too, to [file]. */
    operator fun invoke(file: File) {
        val frame = frameBuffer.frame
        pngFiles.write(file, frame.pixels, frame.width, frame.height)
    }
}
