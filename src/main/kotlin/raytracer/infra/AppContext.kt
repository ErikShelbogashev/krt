package raytracer.infra

import raytracer.app.ChangeCameraSettingsOp
import raytracer.app.ChangeRayTracerSettingsOp
import raytracer.app.ChangeRenderSettingsOp
import raytracer.app.LoadModelOp
import raytracer.app.MoveCameraOp
import raytracer.app.MoveObjectOp
import raytracer.app.RedrawOp
import raytracer.app.RotateCameraOp
import raytracer.app.ToggleWireframeOp
import raytracer.model.Camera
import raytracer.model.FrameBuffer
import raytracer.model.RayTracer
import raytracer.model.RenderController
import raytracer.model.World
import kotlin.random.Random

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
