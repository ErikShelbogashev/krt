package krt.domain.trace

import krt.domain.scene.model.Hit
import krt.domain.scene.model.Sky
import krt.domain.scene.model.Traceable
import krt.platform.math.Ray

/**
 * Scene compiled for tracing (ADR 0008): built once from the objects of the model,
 * immutable afterwards and read by all render threads without locks.
 */
class TraceScene(objects: List<Traceable>, val sky: Sky = Sky.DAYLIGHT, val sampleLights: Boolean = false) {

    private val bvh = Bvh(objects.toTypedArray())

    /** Lights for next event estimation, when [sampleLights] is on. */
    val lights = Lights(if (sampleLights) objects else emptyList())

    /** The hit with the smallest t; on equal t the object that comes first in the scene wins. */
    fun hit(ray: Ray, tMin: Double, tMax: Double): Hit? = bvh.hit(ray, tMin, tMax)
}
