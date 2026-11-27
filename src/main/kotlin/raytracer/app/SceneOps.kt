package raytracer.app

import raytracer.model.Camera
import raytracer.model.Dielectric
import raytracer.model.FrameBuffer
import raytracer.model.ObjParser
import raytracer.model.Point3
import raytracer.model.RayTracer
import raytracer.model.RenderController
import raytracer.model.World
import java.io.File

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
