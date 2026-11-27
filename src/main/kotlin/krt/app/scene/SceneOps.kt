package krt.app.scene

import java.io.File
import krt.domain.camera.Camera
import krt.domain.render.FrameBuffer
import krt.domain.render.RayTracer
import krt.domain.render.RenderController
import krt.domain.render.renderFrame
import krt.domain.scene.World
import krt.domain.scene.model.DemoScene
import krt.i9ns.scenefile.SceneLibrary
import krt.platform.math.Point3

class OpenSceneOp(
    private val world: World,
    private val scenes: SceneLibrary,
    private val camera: Camera,
    private val renderController: RenderController,
    private val rayTracer: RayTracer,
    private val frameBuffer: FrameBuffer,
) {
    /**
     * Replaces the scene with the one in [file] (pbrt-v4, or OBJ with MTL and showcase files beside it) and stands
     * the camera at its view; what krt drew differently from the file is in the result's warnings.
     */
    operator fun invoke(file: File): List<String> {
        val scene = scenes.load(file)
        world.load(scene.objects, scene.look)
        camera.resetView(scene.look)
        renderFrame(renderController, rayTracer, camera, frameBuffer)
        return scene.warnings
    }
}

class MoveObjectOp(
    private val world: World,
    private val renderController: RenderController,
    private val rayTracer: RayTracer,
    private val camera: Camera,
    private val frameBuffer: FrameBuffer,
) {
    operator fun invoke(objectId: String, offset: Point3) {
        world.translateObjectById(objectId, offset)
        renderFrame(renderController, rayTracer, camera, frameBuffer)
    }
}

class LoadSceneOp(
    private val world: World,
    private val scenes: SceneLibrary,
    private val camera: Camera,
    private val renderController: RenderController,
    private val rayTracer: RayTracer,
    private val frameBuffer: FrameBuffer,
) {
    /** Replaces the scene with [scene] and stands the camera at its start view with its lens. */
    operator fun invoke(scene: DemoScene) {
        val description = scenes.load(scene)
        world.load(description.objects, description.look, scene)
        camera.resetView(description.look)
        renderFrame(renderController, rayTracer, camera, frameBuffer)
    }
}
