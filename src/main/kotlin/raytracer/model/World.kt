package raytracer.model

import kotlin.random.Random

/**
 * Scene resource: a single mutable reference to an immutable list of objects (ADR 0005).
 * Writers replace the whole list, readers (the render pool) always see a consistent snapshot.
 */
class World(objects: List<Traceable> = emptyList()) {

    @Volatile
    var objects: List<Traceable> = objects
        private set

    fun translateObjectById(objectId: String, offset: Point3) {
        objects = objects.translateFirst(objectId, offset)
    }

    fun getObjectIds(): List<String> = objects.map { it.id }

    fun add(obj: Traceable) {
        objects = objects + obj
    }

    fun addAll(objs: List<Traceable>) {
        objects = objects + objs
    }

    fun hit(ray: Ray, tMin: Double, tMax: Double): Hit? {
        return objects.asSequence()
            .mapNotNull { it.hit(ray, tMin, tMax) }
            .minByOrNull { it.t }
    }

    companion object {
        fun default(random: Random): World {
            val world = World()
            world.add(Sphere(Point3(0.0, -1000.0, 0.0), 1000.0, Lambert(Color(0.5, 0.5, 0.5))))

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
                        world.add(Sphere(center, 0.2, material))
                    }
                }
            }

            world.add(Sphere(Point3(0.0, 1.0, 0.0), 1.0, Dielectric(1.5)))
            world.add(Sphere(Point3(0.0, 1.0, 2.0), 1.0, Emissive(Color(0.1, 0.7, 0.4), 2.0)))
            world.add(Sphere(Point3(-4.0, 1.0, 0.0), 2.0, Lambert(Color(0.4, 0.2, 0.1)), id = "TEST"))
            world.add(Sphere(Point3(4.0, 1.0, 0.0), 1.0, Metal(Color(0.7, 0.6, 0.5), 0.05)))

            return world
        }
    }
}

private fun List<Traceable>.translateFirst(objectId: String, offset: Point3): List<Traceable> {
    val index = indexOfFirst { it.id == objectId }
    if (index < 0) return this
    return toMutableList().also { it[index] = it[index].translate(offset) }
}
