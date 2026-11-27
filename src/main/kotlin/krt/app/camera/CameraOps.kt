package krt.app.camera

import krt.domain.camera.Camera
import krt.domain.camera.CameraMove
import krt.domain.camera.model.Lens
import krt.domain.camera.model.ViewPreset
import krt.domain.render.FrameBuffer
import krt.domain.render.RayTracer
import krt.domain.render.RenderController
import krt.domain.render.renderFrame
import krt.domain.scene.World

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

class SetCameraViewOp(
    private val camera: Camera,
    private val renderController: RenderController,
    private val rayTracer: RayTracer,
    private val frameBuffer: FrameBuffer,
) {
    operator fun invoke(preset: ViewPreset) {
        camera.lookFrom(preset.origin, preset.target)
        renderFrame(renderController, rayTracer, camera, frameBuffer)
    }
}

class ResetCameraViewOp(
    private val camera: Camera,
    private val renderController: RenderController,
    private val rayTracer: RayTracer,
    private val frameBuffer: FrameBuffer,
) {
    operator fun invoke() {
        camera.resetView()
        renderFrame(renderController, rayTracer, camera, frameBuffer)
    }
}

class FocusOnObjectOp(
    private val world: World,
    private val camera: Camera,
    private val renderController: RenderController,
    private val rayTracer: RayTracer,
    private val frameBuffer: FrameBuffer,
) {
    /** Puts object [objectId] in focus: the focus distance becomes the distance from the camera to its centre. */
    operator fun invoke(objectId: String) {
        val center = world.centerOf(objectId) ?: return
        camera.focusDist = Lens.focusOn(camera.origin, center)
        renderFrame(renderController, rayTracer, camera, frameBuffer)
    }
}
