package krt.domain.scene.model

import kotlin.random.Random
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

class Emissive(private val emitColor: Color, private val intensity: Double) : Material {

    override fun scatter(ray: Ray, hit: Hit, random: Random): Scattered? {
        // Emissive material does not scatter light, it emits light
        return null
    }

    override fun emitted(u: Double, v: Double, p: Point3): Color {
        // Return the color that the material emits, scaled by the intensity
        return emitColor * intensity
    }
}