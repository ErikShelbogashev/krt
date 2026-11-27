package krt.domain.scene.model

import kotlin.random.Random
import krt.platform.math.Color
import krt.platform.math.Point3

/** The objects of the demo scenes: pure functions of the randomness they get. */
@Suppress("MagicNumber") // positions, sizes and colours are the scene
object DemoScenes {

    /** The classic final scene of "Ray Tracing in One Weekend": a field of small spheres and four large ones. */
    fun classic(random: Random): List<Traceable> {
        val objects = mutableListOf<Traceable>(Sphere(Point3(0.0, -1000.0, 0.0), 1000.0, Lambert(Color(0.5, 0.5, 0.5))))
        for (a in -11 until 11) {
            for (b in -11 until 11) {
                val chooseMaterial = random.nextDouble()
                val center = Point3(a + 0.9 * random.nextDouble(), 0.2, b + 0.9 * random.nextDouble())
                if ((center - Point3(4.0, 0.2, 0.0)).length() > 0.9) {
                    val material: Material = when {
                        chooseMaterial < 0.8 -> Lambert(Point3.random(random) * Point3.random(random))
                        chooseMaterial < 0.95 -> Metal(Point3.random(random, 0.5, 1.0), random.nextDouble(0.0, 0.5))
                        else -> Dielectric(1.5)
                    }
                    objects += Sphere(center, 0.2, material)
                }
            }
        }
        objects += Sphere(Point3(0.0, 1.0, 0.0), 1.0, Dielectric(1.5))
        objects += Sphere(Point3(0.0, 1.0, 2.0), 1.0, Emissive(Color(0.1, 0.7, 0.4), 2.0))
        objects += Sphere(Point3(-4.0, 1.0, 0.0), 2.0, Lambert(Color(0.4, 0.2, 0.1)), id = "TEST")
        objects += Sphere(Point3(4.0, 1.0, 0.0), 1.0, Metal(Color(0.7, 0.6, 0.5), 0.05))
        return objects
    }
}
