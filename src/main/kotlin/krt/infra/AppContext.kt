package krt.infra

import kotlin.random.Random
import krt.app.camera.ChangeCameraSettingsOp
import krt.app.camera.MoveCameraOp
import krt.app.camera.RotateCameraOp
import krt.app.render.ChangeRayTracerSettingsOp
import krt.app.render.ChangeRenderSettingsOp
import krt.app.render.RedrawOp
import krt.app.render.ToggleWireframeOp
import krt.app.scene.LoadModelOp
import krt.app.scene.MoveObjectOp
import krt.domain.camera.Camera
import krt.domain.render.FrameBuffer
import krt.domain.render.RayTracer
import krt.domain.render.RenderController
import krt.domain.scene.World

/**
 * Composition root (ADR 0006): creates resources and operations once and wires them together.
 */
class AppContext(
    val world: World,
    val rayTracer: RayTracer,
    val camera: Camera,
    val renderController: RenderController,
    val frameBuffer: FrameBuffer,
) {
    val redraw = RedrawOp(renderController, rayTracer, camera, frameBuffer)
    val rotateCamera = RotateCameraOp(camera, renderController, rayTracer, frameBuffer)
    val moveCamera = MoveCameraOp(camera, renderController, rayTracer, frameBuffer)
    val changeCameraSettings = ChangeCameraSettingsOp(camera, renderController, rayTracer, frameBuffer)
    val loadModel = LoadModelOp(world, renderController, rayTracer, camera, frameBuffer)
    val moveObject = MoveObjectOp(world, renderController, rayTracer, camera, frameBuffer)
    val toggleWireframe = ToggleWireframeOp(renderController, rayTracer, camera, frameBuffer)
    val changeRayTracerSettings = ChangeRayTracerSettingsOp(renderController, rayTracer, camera, frameBuffer)
    val changeRenderSettings = ChangeRenderSettingsOp(renderController, rayTracer, camera, frameBuffer)

    companion object {
        private const val DEFAULT_MAX_DEPTH = 7

        fun create(random: Random = Random.Default): AppContext {
            val world = World.default(random)
            val camera = Camera.default()
            return AppContext(
                world = world,
                rayTracer = RayTracer(world, DEFAULT_MAX_DEPTH),
                camera = camera,
                renderController = RenderController.default(),
                frameBuffer = FrameBuffer(camera.imageWidth, camera.imageHeight),
            )
        }
    }
}
