package raytracer.model

import kotlin.random.Random

class Lambert(private val albedo: Color) : Material {
    override fun scatter(ray: Ray, hit: Hit, random: Random): Scattered {
        val scatterDirection = (hit.normal + Point3.randomInUnitSphere(random)).takeIf { !it.isNearZero() } ?: hit.normal
        val scatteredRay = Ray(hit.p, scatterDirection)
        val attenuation = albedo
        return Scattered(scatteredRay, attenuation)
    }
}
