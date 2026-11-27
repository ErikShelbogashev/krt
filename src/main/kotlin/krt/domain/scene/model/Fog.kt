package krt.domain.scene.model

import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random
import krt.platform.math.Aabb
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

/**
 * Fog, smoke or haze filling a sphere: a homogeneous medium of [density] (the chance per unit of distance that light
 * scatters) whose particles scatter light in every direction, tinted by [albedo]. A ray inside the sphere scatters
 * after a random free path; light that passes through without scattering goes on as if the fog were not there.
 *
 * The free path's random number comes from a hash of the ray, so the hit stays a pure function of its inputs.
 */
class Fog(
    val center: Point3,
    val radius: Double,
    val density: Double,
    val albedo: Color,
    override val id: String = "Fog[${java.util.UUID.randomUUID()}]",
) : Traceable {

    val material = Isotropic(albedo)

    override fun hit(ray: Ray, tMin: Double, tMax: Double): Hit? {
        val oc = ray.origin - center
        val a = ray.direction.dot(ray.direction)
        val halfB = oc.dot(ray.direction)
        val discriminant = halfB * halfB - a * (oc.dot(oc) - radius * radius)
        if (discriminant < 0) return null
        val root = sqrt(discriminant)
        // Where the ray is inside the sphere within [tMin, tMax]; it scatters there after a random free path.
        val enter = maxOf((-halfB - root) / a, tMin)
        val leave = minOf((-halfB + root) / a, tMax)
        val t = if (enter < leave) enter - ln(1.0 - Hashing.uniform(ray)) / density / sqrt(a) else leave
        return if (t < leave) Hit(ray.at(t), t, UP, true, material) else null
    }

    override fun bounds(): Aabb = Aabb(center - Point3(radius, radius, radius), center + Point3(radius, radius, radius))

    override fun translate(offset: Point3): Fog = Fog(center + offset, radius, density, albedo, id)

    override fun linearize(coefficient: Double): List<LineSegment> =
        Sphere(center, radius, material).linearize(coefficient)

    private companion object {
        val UP = Point3(0.0, 1.0, 0.0)
    }
}

/** Particles of a medium: they scatter light equally in every direction, tinted by [albedo]. */
class Isotropic(val albedo: Color) : Material {
    override fun scatter(ray: Ray, hit: Hit, random: Random): Scattered =
        Scattered(ray.next(hit.p, Point3.randomInUnitSphere(random).unit()), albedo, Lobe.VOLUME)
}

/** A pseudo-random number in [0, 1) from the bits of a ray: the same ray, the same number. */
internal object Hashing {
    private const val MIX1 = -0x40a7b892e31b1a47L
    private const val MIX2 = -0x6b2fb644ecceee15L
    private const val GOLDEN = -0x61c8864680b583ebL
    private const val MANTISSA_BITS = 53
    private const val SHIFT_A = 30
    private const val SHIFT_B = 27
    private const val SHIFT_C = 31

    fun uniform(ray: Ray): Double {
        val origin = ray.origin
        val direction = ray.direction
        var h = mix(step(0L, origin.x))
        h = mix(step(h, origin.y))
        h = mix(step(h, origin.z))
        h = mix(step(h, direction.x))
        h = mix(step(h, direction.y))
        h = mix(step(h, direction.z))
        return (h ushr (Long.SIZE_BITS - MANTISSA_BITS)).toDouble() / (1L shl MANTISSA_BITS)
    }

    private fun step(h: Long, value: Double): Long = h + java.lang.Double.doubleToLongBits(value) + GOLDEN

    /** SplitMix64's finaliser. */
    private fun mix(value: Long): Long {
        var z = value
        z = (z xor (z ushr SHIFT_A)) * MIX1
        z = (z xor (z ushr SHIFT_B)) * MIX2
        return z xor (z ushr SHIFT_C)
    }
}
