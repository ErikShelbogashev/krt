package krt.domain.trace

import krt.domain.scene.model.Hit
import krt.domain.scene.model.Traceable
import krt.platform.math.Ray

/**
 * Scene compiled for tracing (ADR 0008): built once from the objects of the model,
 * immutable afterwards and read by all render threads without locks.
 */
class TraceScene(objects: List<Traceable>) {

    private val objects: Array<Traceable> = objects.toTypedArray()

    /**
     * The hit with the smallest t. Each found hit narrows the search interval, so farther objects
     * are rejected early. On equal t the object that comes first wins.
     */
    fun hit(ray: Ray, tMin: Double, tMax: Double): Hit? {
        var closest: Hit? = null
        for (obj in objects) {
            val hit = obj.hit(ray, tMin, closest?.t ?: tMax)
            if (hit != null && (closest == null || hit.t < closest.t)) closest = hit
        }
        return closest
    }
}
