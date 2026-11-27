package krt.domain.scene

import kotlin.random.Random
import krt.domain.scene.model.DemoScenes
import krt.domain.scene.model.Hit
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
class World(objects: List<Traceable> = emptyList(), sky: Sky = Sky.DAYLIGHT) {

    private class Snapshot(val objects: List<Traceable>, val sky: Sky) {
        /** Compiled on first use, so a burst of edits compiles the scene once. */
        val traceScene by lazy { TraceScene(objects, sky) }

        /** Packed for the GPU shader on first use (ADR 0009). */
        val gpuScene by lazy { GpuScenePacking.pack(objects, sky) }
    }

    @Volatile
    private var snapshot = Snapshot(objects, sky)

    val objects: List<Traceable> get() = snapshot.objects

    val sky: Sky get() = snapshot.sky

    /** The scene to trace; consistent with [objects] read at the same moment. */
    val traceScene: TraceScene get() = snapshot.traceScene

    /** The scene for the GPU shader; consistent with [objects] read at the same moment. */
    val gpuScene: GpuScene get() = snapshot.gpuScene

    fun translateObjectById(objectId: String, offset: Point3) {
        snapshot = Snapshot(objects.translateFirst(objectId, offset), sky)
    }

    fun getObjectIds(): List<String> = objects.map { it.id }

    /** Centre of the box around object [objectId], or null when the scene has no such object. */
    fun centerOf(objectId: String): Point3? = objects.find { it.id == objectId }?.bounds()?.center

    fun add(obj: Traceable) {
        snapshot = Snapshot(objects + obj, sky)
    }

    fun addAll(objs: List<Traceable>) {
        snapshot = Snapshot(objects + objs, sky)
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
