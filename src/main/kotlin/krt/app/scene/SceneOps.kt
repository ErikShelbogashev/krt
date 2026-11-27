package krt.app.scene

import java.io.File
import krt.domain.camera.Camera
import krt.domain.render.FrameBuffer
import krt.domain.render.RayTracer
import krt.domain.render.RenderController
import krt.domain.render.renderFrame
import krt.domain.scene.World
import krt.domain.scene.model.DemoScene
import krt.domain.scene.model.Dielectric
import krt.i9ns.objfile.ObjParser
import krt.platform.math.Point3

class LoadModelOp(
    private val world: World,
    private val renderController: RenderController,
    private val rayTracer: RayTracer,
    private val camera: Camera,
    private val frameBuffer: FrameBuffer,
) {
    operator fun invoke(file: File) {
        val triangles = ObjParser(file.absolutePath, LOADED_MODEL_MATERIAL).triangles
        world.addAll(triangles)
        renderFrame(renderController, rayTracer, camera, frameBuffer)
    }

    private companion object {
        val LOADED_MODEL_MATERIAL = Dielectric(1.5)
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
    private val camera: Camera,
    private val renderController: RenderController,
    private val rayTracer: RayTracer,
    private val frameBuffer: FrameBuffer,
) {
    /** Replaces the scene with [scene] and stands the camera at its start view with its lens. */
    operator fun invoke(scene: DemoScene) {
        world.load(scene.objects(), scene.look)
        camera.resetView(scene.look.fov, scene.look.aperture)
        renderFrame(renderController, rayTracer, camera, frameBuffer)
    }
}
