package krt.domain.render

import krt.domain.camera.Camera

/**
 * Domain operation shared by all operations: redraw the current frame (ADR 0004).
 * Not a component: called with the resources of the calling operation.
 */
fun renderFrame(renderController: RenderController, rayTracer: RayTracer, camera: Camera, frameBuffer: FrameBuffer) {
    val frame = frameBuffer.frame
    renderController.renderImage(
        frame,
        rayTracer,
        camera,
        onGpuFrame = { frameBuffer.publishGpu(it) },
        onUpdate = { frameBuffer.publish(frame) },
    )
}
