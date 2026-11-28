package krt.domain.scene

import kotlin.random.Random
import krt.domain.scene.model.DemoScene
import krt.domain.scene.model.DemoScenes
import krt.domain.scene.model.Hit
import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Sky
import krt.domain.scene.model.Traceable
import krt.domain.trace.GpuScene
import krt.domain.trace.GpuScenePacking
import krt.domain.trace.TraceScene
import krt.platform.math.Point3
import krt.platform.math.Ray

/**
 * Scene resource: a single mutable reference to an immutable list of objects (ADR 0005)
 * and the scene compiled from it for tracing (ADR 0008).
 * Writers replace both at once, readers (the render pool) always see a consistent snapshot.
 */
class World(objects: List<Traceable> = emptyList(), look: SceneLook = SceneLook.CLASSIC, source: DemoScene? = null) {

    private class Snapshot(val objects: List<Traceable>, val look: SceneLook, val source: DemoScene?) {
        val sky: Sky get() = look.sky

        /** Compiled on first use, so a burst of edits compiles the scene once. */
        val traceScene by lazy { TraceScene(objects, look) }

        /** Packed for the GPU shader on first use (ADR 0009). */
        val gpuScene by lazy { GpuScenePacking.pack(objects, look) }
        val pack: () -> GpuScene = { gpuScene }
    }

    @Volatile
    private var snapshot = Snapshot(objects, look, source)

    val objects: List<Traceable> get() = snapshot.objects

    val sky: Sky get() = snapshot.sky

    /** How the scene is shown: sky and the lens the camera starts with. */
    val look: SceneLook get() = snapshot.look

    /** The demo scene shown, or null for a scene of one's own. */
    val source: DemoScene? get() = snapshot.source

    /** Replaces the whole scene, with a demo scene ([source]) or a scene of one's own. */
    fun load(objects: List<Traceable>, look: SceneLook, source: DemoScene? = null) {
        snapshot = Snapshot(objects, look, source)
    }

    /** The scene to trace; consistent with [objects] read at the same moment. */
    val traceScene: TraceScene get() = snapshot.traceScene

    /** The scene for the GPU shader; consistent with [objects] read at the same moment. */
    val gpuScene: GpuScene get() = snapshot.gpuScene

    /** Stable for every camera pose of this revision. Invocation packs the captured scene on the render thread. */
    fun gpuSceneForFrame(): () -> GpuScene = snapshot.pack

    fun translateObjectById(objectId: String, offset: Point3) {
        snapshot = Snapshot(objects.translateAll(objectId, offset), look, source)
    }

    fun getObjectIds(): List<String> = objects.map { it.id }

    /** Centre of the box around object [objectId], or null when the scene has no such object. */
    fun centerOf(objectId: String): Point3? = objects.find { it.id == objectId }?.bounds()?.center

    fun add(obj: Traceable) {
        snapshot = Snapshot(objects + obj, look, source)
    }

    fun addAll(objs: List<Traceable>) {
        snapshot = Snapshot(objects + objs, look, source)
    }

    fun hit(ray: Ray, tMin: Double, tMax: Double): Hit? = traceScene.hit(ray, tMin, tMax)

    companion object {
        /** The classic demo scene ([DemoScenes.classic]). */
        fun default(random: Random): World = World(DemoScenes.classic(random))
    }
}

/** Moves every part of object [objectId]: the triangles of a mesh share its id. */
private fun List<Traceable>.translateAll(objectId: String, offset: Point3): List<Traceable> =
    map { if (it.id == objectId) it.translate(offset) else it }
