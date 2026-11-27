package raytracer.app

import raytracer.model.Camera
import raytracer.model.FrameBuffer
import raytracer.model.RayTracer
import raytracer.model.RenderController

class RedrawOp(
    private val renderController: RenderController,
    private val rayTracer: RayTracer,
    private val camera: Camera,
    private val frameBuffer: FrameBuffer,
) {
    operator fun invoke() = renderFrame(renderController, rayTracer, camera, frameBuffer)
}

class ToggleWireframeOp(
    private val renderController: RenderController,
    private val rayTracer: RayTracer,
    private val camera: Camera,
    private val frameBuffer: FrameBuffer,
) {
    operator fun invoke() {
        renderController.toggleWireframeMode()
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
