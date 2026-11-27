package krt.domain.scene

import kotlin.random.Random
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
class World(objects: List<Traceable> = emptyList(), look: SceneLook = SceneLook.CLASSIC) {

    private class Snapshot(val objects: List<Traceable>, val look: SceneLook) {
        val sky: Sky get() = look.sky

        /** Compiled on first use, so a burst of edits compiles the scene once. */
        val traceScene by lazy { TraceScene(objects, look) }

        /** Packed for the GPU shader on first use (ADR 0009). */
        val gpuScene by lazy { GpuScenePacking.pack(objects, look) }
    }

    @Volatile
    private var snapshot = Snapshot(objects, look)

    val objects: List<Traceable> get() = snapshot.objects

    val sky: Sky get() = snapshot.sky

    /** How the scene is shown: sky and the lens the camera starts with. */
    val look: SceneLook get() = snapshot.look

    /** Replaces the whole scene, for instance with another demo scene. */
    fun load(objects: List<Traceable>, look: SceneLook) {
        snapshot = Snapshot(objects, look)
    }

    /** The scene to trace; consistent with [objects] read at the same moment. */
    val traceScene: TraceScene get() = snapshot.traceScene

    /** The scene for the GPU shader; consistent with [objects] read at the same moment. */
    val gpuScene: GpuScene get() = snapshot.gpuScene

    fun translateObjectById(objectId: String, offset: Point3) {
        snapshot = Snapshot(objects.translateFirst(objectId, offset), look)
    }

    fun getObjectIds(): List<String> = objects.map { it.id }

    /** Centre of the box around object [objectId], or null when the scene has no such object. */
    fun centerOf(objectId: String): Point3? = objects.find { it.id == objectId }?.bounds()?.center

    fun add(obj: Traceable) {
        snapshot = Snapshot(objects + obj, look)
    }

    fun addAll(objs: List<Traceable>) {
        snapshot = Snapshot(objects + objs, look)
    }

    fun hit(ray: Ray, tMin: Double, tMax: Double): Hit? = traceScene.hit(ray, tMin, tMax)

    companion object {
        /** The classic demo scene ([DemoScenes.classic]). */
        fun default(random: Random): World = World(DemoScenes.classic(random))
    }
}

private fun List<Traceable>.translateFirst(objectId: String, offset: Point3): List<Traceable> {
    val index = indexOfFirst { it.id == objectId }
    if (index < 0) return this
    return toMutableList().also { it[index] = it[index].translate(offset) }
}
