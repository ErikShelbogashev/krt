package krt.domain.render

import krt.domain.camera.Camera

/**
 * Domain operation shared by all operations: redraw the current frame (ADR 0004).
 * Not a component: called with the resources of the calling operation.
 */
fun renderFrame(renderController: RenderController, rayTracer: RayTracer, camera: Camera, frameBuffer: FrameBuffer) {
    val frame = frameBuffer.beginFrame()
    renderController.renderImage(
        frame,
        rayTracer,
        camera,
        onGpuFrame = { shader, progress -> frameBuffer.publishGpu(shader, progress) },
        onUpdate = { frameBuffer.publish(frame, it) },
    )
}
