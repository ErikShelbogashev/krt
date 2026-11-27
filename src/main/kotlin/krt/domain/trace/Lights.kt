package krt.domain.trace

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import krt.domain.scene.model.Material
import krt.domain.scene.model.Sphere
import krt.domain.scene.model.Traceable
import krt.domain.scene.model.Triangle
import krt.platform.math.Color
import krt.platform.math.Point3

/** A point on a light as seen from a point being lit: unit [direction] to it, its [distance], light, pdf. */
class LightSample(val direction: Point3, val distance: Double, val emitted: Color, val pdf: Double)

/**
 * The lights of a scene for next event estimation: spheres and triangles whose material [Material.emits].
 * A sample picks one light uniformly and a point uniformly on its surface; the pdf is in solid angle at the lit
 * point, with the pick.
 */
class Lights(objects: List<Traceable>) {

    private val lights: Array<Traceable> =
        objects.filter { ((it as? Sphere)?.material ?: (it as? Triangle)?.material)?.emits == true }
            .toTypedArray()

    val count: Int get() = lights.size

    fun sample(point: Point3, random: Random): LightSample? {
        if (lights.isEmpty()) return null
        return when (val light = lights[random.nextInt(lights.size)]) {
            is Sphere -> sphere(light, point, random)
            is Triangle -> triangle(light, point, random)
            else -> null
        }
    }

    private fun sphere(light: Sphere, point: Point3, random: Random): LightSample? {
        val z = 1 - 2 * random.nextDouble()
        val phi = 2 * PI * random.nextDouble()
        val ring = sqrt(maxOf(0.0, 1 - z * z))
        val normal = Point3(ring * cos(phi), ring * sin(phi), z)
        val area = 4 * PI * light.radius * light.radius
        val target = light.center + normal * light.radius
        return seen(point, target, normal, area, light.material, twoSided = false)
    }

    private fun triangle(light: Triangle, point: Point3, random: Random): LightSample? {
        val edge1 = light.v1 - light.v0
        val edge2 = light.v2 - light.v0
        var u = random.nextDouble()
        var v = random.nextDouble()
        if (u + v > 1) {
            u = 1 - u
            v = 1 - v
        }
        val cross = edge1.cross(edge2)
        val doubleArea = cross.length()
        val target = light.v0 + edge1 * u + edge2 * v
        return seen(point, target, cross / doubleArea, doubleArea / 2, light.material, twoSided = true)
    }

    @Suppress("LongParameterList")
    private fun seen(
        point: Point3,
        target: Point3,
        normal: Point3,
        area: Double,
        light: Material,
        twoSided: Boolean,
    ): LightSample? {
        val toLight = target - point
        val distanceSquared = toLight.dot(toLight)
        val distance = sqrt(distanceSquared)
        val direction = toLight / distance
        val facing = -direction.dot(normal)
        val cosine = if (twoSided) abs(facing) else facing
        if (cosine <= MIN_COSINE) return null
        val pdf = distanceSquared / (cosine * area) / lights.size
        return LightSample(direction, distance, light.emitted(0.0, 0.0, target), pdf)
    }

    private companion object {
        const val MIN_COSINE = 1e-6
    }
}
