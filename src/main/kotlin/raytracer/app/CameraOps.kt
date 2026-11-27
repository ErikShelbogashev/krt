package raytracer.app

import raytracer.model.Camera
import raytracer.model.CameraMove
import raytracer.model.FrameBuffer
import raytracer.model.RayTracer
import raytracer.model.RenderController

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
