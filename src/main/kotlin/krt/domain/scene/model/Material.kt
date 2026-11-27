package krt.domain.scene.model

import kotlin.random.Random
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

data class Scattered(val ray: Ray, val attenuation: Color)

interface Material {
    fun scatter(ray: Ray, hit: Hit, random: Random): Scattered?
    fun emitted(u: Double, v: Double, p: Point3): Color {
        return Color(0.0, 0.0, 0.0) // Default to no emission
    }
}