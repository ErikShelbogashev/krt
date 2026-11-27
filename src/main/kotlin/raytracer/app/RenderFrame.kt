package raytracer.app

import raytracer.model.Camera
import raytracer.model.FrameBuffer
import raytracer.model.RayTracer
import raytracer.model.RenderController

/**
 * Domain operation shared by all operations: redraw the current frame (ADR 0004).
 * Not a component: called with the resources of the calling operation.
 */
fun renderFrame(renderController: RenderController, rayTracer: RayTracer, camera: Camera, frameBuffer: FrameBuffer) {
    val image = frameBuffer.image
    renderController.renderImage(image, rayTracer, camera) { frameBuffer.publish(image) }
}
