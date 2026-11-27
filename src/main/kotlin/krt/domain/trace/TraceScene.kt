package krt.domain.trace

import krt.domain.scene.model.Hit
import krt.domain.scene.model.Traceable
import krt.platform.math.Ray

/**
 * Scene compiled for tracing (ADR 0008): built once from the objects of the model,
 * immutable afterwards and read by all render threads without locks.
 */
class TraceScene(objects: List<Traceable>) {

    private val bvh = Bvh(objects.toTypedArray())

    /** The hit with the smallest t; on equal t the object that comes first in the scene wins. */
    fun hit(ray: Ray, tMin: Double, tMax: Double): Hit? = bvh.hit(ray, tMin, tMax)
}
