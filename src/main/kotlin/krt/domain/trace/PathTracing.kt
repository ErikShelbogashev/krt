package krt.domain.trace

import kotlin.random.Random
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

/** Colour seen along a ray: the path tracing recursion of the kernel (ADR 0008). */
object PathTracing {

    fun color(scene: TraceScene, ray: Ray, depth: Int, random: Random): Color {
        if (depth <= 0) return Point3.ZERO

        val hitRecord = scene.hit(ray, T_MIN, Double.POSITIVE_INFINITY)
        return if (hitRecord != null) {
            val scattered = hitRecord.material.scatter(ray, hitRecord, random)
            val emitted = hitRecord.material.emitted(0.0, 0.0, hitRecord.p) // u and v are set to 0.0 for simplicity
            if (scattered != null) {
                emitted + scattered.attenuation * color(scene, scattered.ray, depth - 1, random)
            } else {
                emitted
            }
        } else {
            scene.sky.color(ray.direction)
        }
    }

    /** Hits closer than this are self-intersections of the surface the ray starts on. */
    private const val T_MIN = 0.001
}
