package krt.domain.trace

import krt.domain.scene.model.Fog
import krt.domain.scene.model.Hit
import krt.domain.scene.model.Medium
import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Sky
import krt.domain.scene.model.Traceable
import krt.platform.math.Point3
import krt.platform.math.Ray

/**
 * Scene compiled for tracing (ADR 0008): built once from the objects of the model,
 * immutable afterwards and read by all render threads without locks.
 */
class TraceScene(objects: List<Traceable>, val look: SceneLook = SceneLook.CLASSIC) {

    private val bvh = Bvh(objects.toTypedArray())

    val sky: Sky get() = look.sky

    /** Whether light paths sample the lights directly (ADR 0012). */
    val sampleLights: Boolean get() = look.sampleLights

    /** Banks of fog, so a ray that starts inside one starts in its medium (ADR 0014). */
    private val fogs = objects.filterIsInstance<Fog>().toTypedArray()

    /** The medium at [point]: that of a bank of fog around it, or null in clear air. */
    fun mediumAt(point: Point3): Medium? = fogs.firstOrNull { it.contains(point) }?.medium

    /** Lights for next event estimation, when [sampleLights] is on. */
    val lights = Lights(if (sampleLights) objects else emptyList())

    /** The hit with the smallest t; on equal t the object that comes first in the scene wins. */
    fun hit(ray: Ray, tMin: Double, tMax: Double): Hit? = bvh.hit(ray, tMin, tMax)
}
