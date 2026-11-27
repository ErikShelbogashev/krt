package krt.domain.trace

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import krt.domain.scene.World
import krt.domain.scene.model.Hit
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Metal
import krt.domain.scene.model.Sphere
import krt.domain.scene.model.Traceable
import krt.domain.scene.model.Triangle
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

class TraceSceneTest {

    @Test
    fun `finds the same hit as a scan of all objects`() {
        val random = Random(3)
        val objects = World.default(Random(42)).objects + List(TRIANGLES) { randomTriangle(random) }
        val scene = TraceScene(objects)

        repeat(RAYS) {
            val ray = Ray(Point3.random(random, -RANGE, RANGE), Point3.random(random, -1.0, 1.0))
            assertSameHit(scan(objects, ray), scene.hit(ray, T_MIN, Double.POSITIVE_INFINITY))
        }
    }

    @Test
    fun `on equal t the object that comes first wins`() {
        val first = Lambert(Color(1.0, 0.0, 0.0))
        val second = Metal(Color(0.0, 1.0, 0.0), 0.0)
        val objects = List(SAME_PLACE) { i -> Sphere(Point3(0.0, 0.0, -5.0), 1.0, if (i == 0) first else second) }

        val hit = TraceScene(objects).hit(Ray(Point3.ZERO, Point3(0.0, 0.0, -1.0)), T_MIN, INF)

        assertSame(first, hit?.material)
    }

    @Test
    fun `an empty scene is never hit`() {
        assertNull(TraceScene(emptyList()).hit(Ray(Point3.ZERO, Point3(0.0, 0.0, -1.0)), T_MIN, INF))
    }

    private fun scan(objects: List<Traceable>, ray: Ray): Hit? =
        objects.mapNotNull { it.hit(ray, T_MIN, INF) }.minByOrNull { it.t }

    private fun assertSameHit(expected: Hit?, actual: Hit?) {
        assertEquals(expected?.t, actual?.t)
        assertEquals(expected?.p, actual?.p)
        assertEquals(expected?.normal, actual?.normal)
        assertSame(expected?.material, actual?.material)
    }

    private fun randomTriangle(random: Random): Triangle {
        val corner = Point3.random(random, -RANGE, RANGE)
        return Triangle(
            corner,
            corner + Point3.random(random, -1.0, 1.0),
            corner + Point3.random(random, -1.0, 1.0),
            Lambert(Color(0.5, 0.5, 0.5)),
        )
    }

    private companion object {
        const val TRIANGLES = 300
        const val RAYS = 5_000
        const val RANGE = 12.0
        const val SAME_PLACE = 9
        const val T_MIN = 0.001
        const val INF = Double.POSITIVE_INFINITY
    }
}
