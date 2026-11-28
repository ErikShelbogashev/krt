package krt.domain.scene.model

import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

/**
 * Glossy plastic: a diffuse [albedo] under a clear coat. The coat reflects white light with the Fresnel chance of
 * its refraction index [ior] (Schlick), sharper the lower its [roughness]; the rest of the light reaches the base.
 */
class Plastic(val albedo: Color, val roughness: Double, val ior: Double = COAT_IOR) : Material {
    override fun scatter(ray: Ray, hit: Hit, random: Random): Scattered? =
        ClearCoat.scatter(ray, hit, albedo, roughness, ior, random)

    companion object {
        const val COAT_IOR = 1.5
    }
}

/**
 * A checkerboard of [even] and [odd] cells [cellSize] wide under a clear coat, like a polished tiled floor.
 * The cells are cubes in space, so the pattern lies on any surface.
 */
class Checker(
    val even: Color,
    val odd: Color,
    val cellSize: Double,
    val roughness: Double,
    val ior: Double = Plastic.COAT_IOR,
) : Material {
    override fun scatter(ray: Ray, hit: Hit, random: Random): Scattered? =
        ClearCoat.scatter(ray, hit, colorAt(hit.p), roughness, ior, random)

    fun colorAt(p: Point3): Color {
        val cells = floor(p.x / cellSize) + floor(p.y / cellSize) + floor(p.z / cellSize)
        return if (cells.mod(2.0) == 0.0) even else odd
    }
}

/** Scattering of a clear coat over a diffuse base: the shared rule of [Plastic] and [Checker]. */
internal object ClearCoat {

    private val WHITE = Color(1.0, 1.0, 1.0)
    private const val FRESNEL_POWER = 5

    fun scatter(ray: Ray, hit: Hit, base: Color, roughness: Double, ior: Double, random: Random): Scattered? {
        val unit = ray.direction.unit()
        val cosine = min(-unit.dot(hit.normal), 1.0)
        if (random.nextDouble() >= reflectance(cosine, ior)) return diffuse(ray, hit, base, random)
        val reflected = unit.reflect(hit.normal) + Point3.randomInUnitSphere(random) * roughness
        return if (reflected.dot(hit.normal) > 0) Scattered(ray.next(hit.p, reflected), WHITE) else null
    }

    /** Schlick's approximation of the Fresnel reflectance at the coat. */
    fun reflectance(cosine: Double, ior: Double): Double {
        val r0 = ((1 - ior) / (1 + ior)).let { it * it }
        return r0 + (1 - r0) * (1 - cosine).pow(FRESNEL_POWER)
    }

    private fun diffuse(ray: Ray, hit: Hit, base: Color, random: Random): Scattered {
        val direction = hit.normal.cosineHemisphere(random)
        return Scattered(ray.next(hit.p, direction), base, Lobe.DIFFUSE)
    }
}
