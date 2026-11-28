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
 * The lights of a scene for next event estimation (ADR 0012): spheres and objects of triangles whose material
 * [Material.emits], grouped into [Emitter]s. A sample picks a light in proportion to its area times its brightness,
 * then a point uniformly on its surface; the pdf is in solid angle at the lit point, with the pick.
 */
class Lights(objects: List<Traceable>) {

    private val emitters = Emitters.of(objects).toTypedArray()
    private val total = emitters.sumOf { it.weight }

    /** Cumulative weights of the lights, from 0 to [total]. */
    private val cumulative = emitters.runningFold(0.0) { sum, emitter -> sum + emitter.weight }.drop(1).toDoubleArray()

    /** Cumulative areas of the members of each light. */
    private val memberCumulative = emitters.map { emitter ->
        emitter.areas.runningFold(0.0) { sum, area -> sum + area }.drop(1).toDoubleArray()
    }

    val count: Int get() = emitters.size

    /** A light sample seen from [point] at moment [time] of the shutter, when moving lights are where they are. */
    fun sample(point: Point3, time: Double, random: Random): LightSample? {
        if (emitters.isEmpty()) return null
        val index = pick(cumulative, random.nextDouble() * total)
        val emitter = emitters[index]
        val members = memberCumulative[index]
        val member = emitter.members[pick(members, random.nextDouble() * emitter.area)]
        val chance = emitter.weight / total / emitter.area
        return when (member) {
            is Sphere -> sphere(member, point, time, random, chance)
            is Triangle -> triangle(member, point, random, chance)
            else -> null
        }
    }

    /** The first index whose cumulative value exceeds [value]: a binary search. */
    private fun pick(cumulative: DoubleArray, value: Double): Int {
        var low = 0
        var high = cumulative.size - 1
        while (low < high) {
            val middle = (low + high) / 2
            if (cumulative[middle] > value) high = middle else low = middle + 1
        }
        return low
    }

    /** A point on [light], whose area density is [chance] with the pick of its light. */
    private fun sphere(light: Sphere, point: Point3, time: Double, random: Random, chance: Double): LightSample? {
        val z = 1 - 2 * random.nextDouble()
        val phi = 2 * PI * random.nextDouble()
        val ring = sqrt(maxOf(0.0, 1 - z * z))
        val normal = Point3(ring * cos(phi), ring * sin(phi), z)
        val target = light.centerAt(time) + normal * light.radius
        return seen(point, target, normal, chance, light.material, twoSided = false)
    }

    private fun triangle(light: Triangle, point: Point3, random: Random, chance: Double): LightSample? {
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
        return seen(point, target, cross / doubleArea, chance, light.material, twoSided = true)
    }

    @Suppress("LongParameterList")
    private fun seen(
        point: Point3,
        target: Point3,
        normal: Point3,
        chance: Double,
        light: Material,
        twoSided: Boolean,
    ): LightSample? {
        val toLight = target - point
        val distanceSquared = toLight.dot(toLight)
        val distance = sqrt(distanceSquared)
        val direction = toLight / distance
        val facing = -direction.dot(normal)
        val cosine = if (twoSided) abs(facing) else facing
        if (distanceSquared == 0.0 || cosine <= MIN_COSINE) return null
        val pdf = distanceSquared / cosine * chance
        return LightSample(direction, distance, light.emitted(0.0, 0.0, target), pdf)
    }

    private companion object {
        const val MIN_COSINE = 1e-6
    }
}
