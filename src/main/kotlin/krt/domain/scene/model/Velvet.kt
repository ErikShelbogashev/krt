package krt.domain.scene.model

import kotlin.math.PI
import kotlin.math.pow
import kotlin.random.Random
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

/**
 * Velvet or cloth: a diffuse [base] under fibres that catch light at grazing angles and glow with [sheen] at the
 * rims, the wider the higher their [roughness] (Charlie sheen of Estevez and Kulla, ADR 0013).
 *
 * Each bounce picks one of the two: the diffuse one, which light sampling can light directly, or the sheen one,
 * sampled by the cosine and weighed by the sheen BRDF; each is divided by the chance of picking it.
 */
class Velvet(val base: Color, val sheen: Color, val roughness: Double) : Material {

    /** Chance of the diffuse bounce. */
    val diffuseChance: Double = Sheen.diffuseChance(base, sheen)

    override fun scatter(ray: Ray, hit: Hit, random: Random): Scattered {
        val diffuse = random.nextDouble() < diffuseChance
        val direction = hit.normal.cosineHemisphere(random)
        if (diffuse) {
            return Scattered(ray.next(hit.p, direction), base / diffuseChance, Lobe.DIFFUSE)
        }
        val weight = Sheen.weight(-ray.direction.unit(), direction.unit(), hit.normal, roughness)
        return Scattered(ray.next(hit.p, direction), sheen * (weight / (1 - diffuseChance)))
    }
}

/** The sheen lobe of [Velvet]. */
object Sheen {
    private const val MIN_CHANCE = 0.1
    private const val MAX_CHANCE = 0.9
    private const val MIN_ALPHA = 1e-3

    fun diffuseChance(base: Color, sheen: Color): Double {
        val diffuse = base.x + base.y + base.z
        return (diffuse / (diffuse + sheen.x + sheen.y + sheen.z)).coerceIn(MIN_CHANCE, MAX_CHANCE)
    }

    /**
     * BRDF × cosine / pdf of a direction sampled by the cosine: the Charlie distribution with Neubelt's visibility,
     * times π, between unit [toViewer] and [toLight] around [normal].
     */
    fun weight(toViewer: Point3, toLight: Point3, normal: Point3, roughness: Double): Double {
        val cosViewer = toViewer.dot(normal).coerceAtLeast(0.0)
        val cosLight = toLight.dot(normal).coerceAtLeast(0.0)
        val half = (toViewer + toLight).unit()
        val cosHalf = half.dot(normal)
        val inverseAlpha = 1 / maxOf(roughness * roughness, MIN_ALPHA)
        val sinHalf = (1 - cosHalf * cosHalf).coerceAtLeast(0.0)
        val distribution = (2 + inverseAlpha) * sinHalf.pow(inverseAlpha / 2) / (2 * PI)
        val visibility = 1 / (4 * (cosLight + cosViewer - cosLight * cosViewer)).coerceAtLeast(MIN_ALPHA)
        return distribution * visibility * PI
    }
}
