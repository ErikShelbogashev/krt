package krt.infra

import krt.app.camera.ChangeCameraSettingsOp
import krt.app.camera.FocusOnObjectOp
import krt.app.camera.MoveCameraOp
import krt.app.camera.ResetCameraViewOp
import krt.app.camera.RotateCameraOp
import krt.app.camera.SetCameraViewOp
import krt.app.render.ChangeRayTracerSettingsOp
import krt.app.render.ChangeRenderSettingsOp
import krt.app.render.CaptureSettingsOp
import krt.app.render.FlyCameraOp
import krt.app.render.RedrawOp
import krt.app.render.ReportGpuFrameOp
import krt.app.render.RestoreSettingsOp
import krt.app.render.SaveBenchmarkReportOp
import krt.app.render.StartBenchmarkRunOp
import krt.app.render.SaveImageOp
import krt.app.render.SetRenderModeOp
import krt.app.scene.LoadSceneOp
import krt.app.scene.MoveObjectOp
import krt.app.scene.OpenSceneOp
import krt.domain.camera.Camera
import krt.domain.render.FrameBuffer
import krt.domain.render.RayTracer
import krt.domain.render.RenderController
import krt.domain.scene.World
import krt.domain.scene.model.DemoScene
import krt.i9ns.png.PngFiles
import krt.i9ns.scenefile.SceneLibrary
import krt.i9ns.textfile.TextFiles

/**
 * Composition root (ADR 0006): creates resources and operations once and wires them together.
 * Closing it releases the resources it created.
 */
class AppContext(
    val world: World,
    val rayTracer: RayTracer,
    val camera: Camera,
    val renderController: RenderController,
    val frameBuffer: FrameBuffer,
    val scenes: SceneLibrary = SceneLibrary(),
    pngFiles: PngFiles = PngFiles(),
    textFiles: TextFiles = TextFiles(),
) : AutoCloseable {
    val redraw = RedrawOp(renderController, rayTracer, camera, frameBuffer)
    val rotateCamera = RotateCameraOp(camera, renderController, rayTracer, frameBuffer)
    val moveCamera = MoveCameraOp(camera, renderController, rayTracer, frameBuffer)
    val changeCameraSettings = ChangeCameraSettingsOp(camera, renderController, rayTracer, frameBuffer)
    val setCameraView = SetCameraViewOp(camera, renderController, rayTracer, frameBuffer)
    val resetCameraView = ResetCameraViewOp(world, camera, renderController, rayTracer, frameBuffer)
    val loadScene = LoadSceneOp(world, scenes, camera, renderController, rayTracer, frameBuffer)
    val captureSettings = CaptureSettingsOp(world, camera, renderController, rayTracer)
    val startBenchmarkRun = StartBenchmarkRunOp(world, scenes, camera, renderController, rayTracer, frameBuffer)
    val flyCamera = FlyCameraOp(camera, renderController, rayTracer, frameBuffer)
    val restoreSettings = RestoreSettingsOp(world, camera, renderController, rayTracer, frameBuffer)
    val saveBenchmarkReport = SaveBenchmarkReportOp(textFiles)
    val focusOnObject = FocusOnObjectOp(world, camera, renderController, rayTracer, frameBuffer)
    val openScene = OpenSceneOp(world, scenes, camera, renderController, rayTracer, frameBuffer)
    val moveObject = MoveObjectOp(world, renderController, rayTracer, camera, frameBuffer)
    val setRenderMode = SetRenderModeOp(renderController, rayTracer, camera, frameBuffer)
    val reportGpuFrame = ReportGpuFrameOp(renderController)
    val saveImage = SaveImageOp(renderController, rayTracer, camera, frameBuffer, pngFiles)
    val changeRayTracerSettings = ChangeRayTracerSettingsOp(renderController, rayTracer, camera, frameBuffer)
    val changeRenderSettings = ChangeRenderSettingsOp(renderController, rayTracer, camera, frameBuffer)

    override fun close() {
        renderController.close()
    }

    companion object {
        private const val DEFAULT_MAX_DEPTH = 7

        /** The window's context, starting with [scene], laid out the same way every time. */
        fun create(scene: DemoScene = DemoScene.SHOWCASE): AppContext {
            val scenes = SceneLibrary()
            val description = scenes.load(scene)
            val world = World(description.objects, description.look, scene)
            val camera = Camera.default().apply { resetView(description.look) }
            return AppContext(
                scenes = scenes,
                world = world,
                rayTracer = RayTracer(world, DEFAULT_MAX_DEPTH),
                camera = camera,
                renderController = RenderController.default(),
                frameBuffer = FrameBuffer(camera.imageWidth, camera.imageHeight),
            )
        }
    }
}
