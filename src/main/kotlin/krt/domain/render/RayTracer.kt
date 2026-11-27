package krt.domain.render

import kotlin.random.Random
import krt.domain.scene.World
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

class RayTracer(val world: World, var maxDepth: Int) {
    fun color(ray: Ray, random: Random): Color {
        return colorRecursively(ray, maxDepth, random)
    }

    private fun colorRecursively(ray: Ray, depth: Int, random: Random): Color {
        if (depth <= 0) return Point3.ZERO

        val hitRecord = world.hit(ray, 0.001, Double.POSITIVE_INFINITY)
        return if (hitRecord != null) {
            val scattered = hitRecord.material.scatter(ray, hitRecord, random)
            val emitted = hitRecord.material.emitted(0.0, 0.0, hitRecord.p) // u and v are set to 0.0 for simplicity
            if (scattered != null) {
                emitted + scattered.attenuation * colorRecursively(scattered.ray, depth - 1, random)
            } else {
                emitted
            }
        } else {
            val unitDirection = ray.direction.unit()
            val t = 0.5 * (unitDirection.y + 1.0)
            Color(1.0, 1.0, 1.0) * (1.0 - t) + Color(0.5, 0.7, 1.0) * t
        }
    }
}