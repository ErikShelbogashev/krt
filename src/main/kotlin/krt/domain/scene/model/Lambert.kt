package krt.domain.scene.model

import kotlin.random.Random
import krt.platform.math.Color
import krt.platform.math.Ray

class Lambert(val albedo: Color) : Material {
    override fun scatter(ray: Ray, hit: Hit, random: Random): Scattered {
        val scatterDirection = hit.normal.cosineHemisphere(random)
        val scatteredRay = ray.next(hit.p, scatterDirection)
        val attenuation = albedo
        return Scattered(scatteredRay, attenuation, Lobe.DIFFUSE)
    }
}
