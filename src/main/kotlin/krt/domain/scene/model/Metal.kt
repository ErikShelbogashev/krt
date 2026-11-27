package krt.domain.scene.model

import kotlin.random.Random
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

class Metal(val albedo: Color, f: Double) : Material {
    val fuzz: Double = if (f < 1) f else 1.0

    override fun scatter(ray: Ray, hit: Hit, random: Random): Scattered? {
        val reflected = ray.direction.unit().reflect(hit.normal.unit())
        val scatterDirection = reflected + Point3.randomInUnitSphere(random) * fuzz
        val scatteredRay = Ray(hit.p, scatterDirection)
        val attenuation = albedo
        return if (scatterDirection.dot(hit.normal) > 0) {
            Scattered(scatteredRay, attenuation)
        } else {
            null
        }
    }
}
