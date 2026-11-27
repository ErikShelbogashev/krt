package krt.bench

import java.io.File
import kotlin.random.Random
import krt.domain.camera.Camera
import krt.domain.scene.World
import krt.domain.scene.model.Dielectric
import krt.domain.scene.model.Triangle
import krt.i9ns.objfile.ObjText

/** Scenes and camera shared by the benchmarks; everything is seeded, so runs are comparable. */
internal object Scenes {
    const val DEMO = "demo"
    const val DEMO_WITH_MODEL = "demo+model"

    private const val SAMPLE_MODEL = "samples/ORIGAMI_Chat_Free.obj"

    fun world(name: String): World {
        val world = World.default(Random(WORLD_SEED))
        if (name == DEMO_WITH_MODEL) {
            val root = System.getProperty("krt.projectDir") ?: "."
            world.addAll(model(File(root, SAMPLE_MODEL).readText()))
        }
        return world
    }

    /** Smaller than the app's default 400×400 so that one benchmark run takes minutes, not hours. */
    fun camera(): Camera = Camera.default().apply {
        imageWidth = FRAME_SIZE
        imageHeight = FRAME_SIZE
    }

    const val MAX_DEPTH = 7
    private const val FRAME_SIZE = 160
    private const val WORLD_SEED = 42
    private const val MODEL_REFRACTION = 1.5

    /** The sample model as the benchmarks have always taken it: scaled into [-1, 1], all of glass. */
    private fun model(text: String): List<Triangle> {
        val obj = ObjText.obj(text)
        val extent = obj.points.maxOf { maxOf(kotlin.math.abs(it.x), kotlin.math.abs(it.y), kotlin.math.abs(it.z)) }
        val glass = Dielectric(MODEL_REFRACTION)
        return obj.objects.flatMap { it.faces }.map { face ->
            val (a, b, c) = face.points.map { obj.points[it] / extent }
            Triangle(a, b, c, glass)
        }
    }
}
