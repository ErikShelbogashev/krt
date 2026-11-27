package krt.domain.scene.model

import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random
import krt.platform.math.Point3
import krt.platform.math.Ray

class Dielectric(private val indexOfReflection: Double) : Material {
    override fun scatter(ray: Ray, hit: Hit, random: Random): Scattered {
        val attenuation = Point3.ONE
        val refractionRatio = if (hit.isFrontFace) 1.0 / indexOfReflection else indexOfReflection

        val unitDirection = ray.direction.unit()
        val cosTheta = min(-unitDirection.dot(hit.normal), 1.0)
        val sinTheta = sqrt(1.0 - cosTheta * cosTheta)

        val cannotRefract = refractionRatio * sinTheta > 1.0
        val shouldReflect = cannotRefract || reflectance(cosTheta, refractionRatio) > random.nextDouble()
        val direction = if (shouldReflect) {
            unitDirection.reflect(hit.normal)
        } else {
            unitDirection.refract(hit.normal, refractionRatio)
        }

        return Scattered(Ray(hit.p, direction), attenuation)
    }

    private fun reflectance(cosine: Double, refIdx: Double): Double {
        val r0 = ((1 - refIdx) / (1 + refIdx)).let { it * it }
        return r0 + (1 - r0) * (1 - cosine).pow(5)
    }
}
