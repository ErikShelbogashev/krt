package krt.domain.scene.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

/**
 * A soap bubble: a film [thickness] nm thin of refraction index [filmIor] with air on both sides. Light reflected
 * off its two faces interferes, so the reflection takes colours that change with the angle; the rest passes
 * straight through.
 */
class SoapBubble(val thickness: Double, val filmIor: Double = WATER_IOR) : Material {
    override fun scatter(ray: Ray, hit: Hit, random: Random): Scattered {
        val unit = ray.direction.unit()
        val reflectance = ThinFilm.reflectance(ray, hit, thickness, filmIor, AIR_IOR)
        val chance = ThinFilm.chance(reflectance)
        return if (random.nextDouble() < chance) {
            Scattered(ray.next(hit.p, unit.reflect(hit.normal)), reflectance / chance)
        } else {
            Scattered(ray.next(hit.p, unit), (Point3.ONE - reflectance) / (1 - chance))
        }
    }

    private companion object {
        const val WATER_IOR = 1.33
        const val AIR_IOR = 1.0
    }
}

/**
 * An iridescent coat, like oil on a puddle or a beetle's shell: a film [thickness] nm thin of index [filmIor] over
 * a diffuse [base]. The film reflects colours that change with the angle, sharper the lower its [roughness];
 * what it does not reflect lights the base.
 */
class Iridescent(val base: Color, val thickness: Double, val filmIor: Double, val roughness: Double = 0.0) : Material {
    override fun scatter(ray: Ray, hit: Hit, random: Random): Scattered? {
        val reflectance = ThinFilm.reflectance(ray, hit, thickness, filmIor, SUBSTRATE_IOR)
        val chance = ThinFilm.chance(reflectance)
        if (random.nextDouble() >= chance) {
            val direction = (hit.normal + Point3.randomInUnitSphere(random)).takeIf { !it.isNearZero() } ?: hit.normal
            return Scattered(ray.next(hit.p, direction), base * (Point3.ONE - reflectance) / (1 - chance), Lobe.DIFFUSE)
        }
        val reflected = ray.direction.unit().reflect(hit.normal) + Point3.randomInUnitSphere(random) * roughness
        return if (reflected.dot(hit.normal) > 0) Scattered(ray.next(hit.p, reflected), reflectance / chance) else null
    }

    private companion object {
        const val SUBSTRATE_IOR = 1.5
    }
}

/** Reflectance of a thin film between air and a substrate: Airy's sum of the waves off its two faces (ADR 0013). */
object ThinFilm {

    /**
     * Reflectance per channel where [ray] meets the film at [hit]: at the wavelength the ray carries, or at the
     * wavelengths of the red, green and blue channels for white light.
     */
    fun reflectance(ray: Ray, hit: Hit, thickness: Double, filmIor: Double, substrateIor: Double): Color {
        val cosine = min(-ray.direction.unit().dot(hit.normal), 1.0)
        return if (ray.wavelength > 0) {
            Point3.ONE * at(cosine, thickness, filmIor, substrateIor, ray.wavelength)
        } else {
            Color(
                at(cosine, thickness, filmIor, substrateIor, Spectrum.RED_NM),
                at(cosine, thickness, filmIor, substrateIor, Spectrum.GREEN_NM),
                at(cosine, thickness, filmIor, substrateIor, Spectrum.BLUE_NM),
            )
        }
    }

    /** The chance to follow the reflection: the mean reflectance, kept off 0 and 1 so neither branch starves. */
    fun chance(reflectance: Color): Double =
        ((reflectance.x + reflectance.y + reflectance.z) / CHANNELS).coerceIn(MIN, MAX)

    /** Reflectance at [wavelength] nm for light arriving at [cosine] to the normal, unpolarised. */
    fun at(cosine: Double, thickness: Double, filmIor: Double, substrateIor: Double, wavelength: Double): Double {
        val sinSquared = 1 - cosine * cosine
        val inFilm = sqrt(maxOf(0.0, 1 - sinSquared / (filmIor * filmIor)))
        val inSubstrate = sqrt(maxOf(0.0, 1 - sinSquared / (substrateIor * substrateIor)))
        val phase = 4 * PI * filmIor * thickness * inFilm / wavelength
        val s = airy(
            fresnel(1.0, cosine, filmIor, inFilm),
            fresnel(filmIor, inFilm, substrateIor, inSubstrate),
            phase,
        )
        val p = airy(
            fresnel(1.0, inFilm, filmIor, cosine),
            fresnel(filmIor, inSubstrate, substrateIor, inFilm),
            phase,
        )
        return (s + p) / 2
    }

    /** Fresnel amplitude of an interface; with the cosines swapped it is the p-polarised one. */
    private fun fresnel(n1: Double, cos1: Double, n2: Double, cos2: Double): Double =
        (n1 * cos1 - n2 * cos2) / (n1 * cos1 + n2 * cos2)

    private fun airy(r12: Double, r23: Double, phase: Double): Double {
        val cross = 2 * r12 * r23 * cos(phase)
        return (r12 * r12 + r23 * r23 + cross) / (1 + r12 * r12 * r23 * r23 + cross)
    }

    private const val CHANNELS = 3
    private const val MIN = 0.02
    private const val MAX = 0.98
}
