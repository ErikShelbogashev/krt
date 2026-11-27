package krt.app.camera

import krt.domain.camera.Camera
import krt.domain.camera.CameraMove
import krt.domain.render.FrameBuffer
import krt.domain.render.RayTracer
import krt.domain.render.RenderController
import krt.domain.render.renderFrame

class RotateCameraOp(
    private val camera: Camera,
    private val renderController: RenderController,
    private val rayTracer: RayTracer,
    private val frameBuffer: FrameBuffer,
) {
    operator fun invoke(dxPixels: Int, dyPixels: Int) {
        camera.rotateByDrag(dxPixels, dyPixels)
        renderFrame(renderController, rayTracer, camera, frameBuffer)
    }
}

class MoveCameraOp(
    private val camera: Camera,
    private val renderController: RenderController,
    private val rayTracer: RayTracer,
    private val frameBuffer: FrameBuffer,
) {
    operator fun invoke(move: CameraMove) {
        camera.move(move)
        renderFrame(renderController, rayTracer, camera, frameBuffer)
    }
}

class ChangeCameraSettingsOp(
    private val camera: Camera,
    private val renderController: RenderController,
    private val rayTracer: RayTracer,
    private val frameBuffer: FrameBuffer,
) {
    operator fun invoke(change: Camera.() -> Unit) {
        camera.change()
        if (!camera.hasRenderableSize()) return
        frameBuffer.resizeTo(camera.imageWidth, camera.imageHeight)
        renderFrame(renderController, rayTracer, camera, frameBuffer)
    }
}
