package krt.domain.scene

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import krt.domain.render.RayTracer
import krt.domain.scene.model.Dielectric
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.LineSegment
import krt.domain.scene.model.Sphere
import krt.domain.scene.model.Triangle
import krt.platform.awt.toAWTColor
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

class GeometryTest {

    private val eps = 1e-9

    private fun assertPoint(expected: Point3, actual: Point3) {
        assertTrue(
            abs(expected.x - actual.x) < eps && abs(expected.y - actual.y) < eps && abs(expected.z - actual.z) < eps,
            "expected $expected but was $actual"
        )
    }

    @Test
    fun `vector operations`() {
        val a = Point3(1.0, 2.0, 3.0)
        val b = Point3(4.0, 5.0, 6.0)
        assertPoint(Point3(5.0, 7.0, 9.0), a + b)
        assertPoint(Point3(-3.0, -3.0, -3.0), a - b)
        assertEquals(32.0, a.dot(b))
        assertPoint(Point3(-3.0, 6.0, -3.0), a.cross(b))
        assertEquals(1.0, Point3(3.0, 4.0, 0.0).unit().length(), eps)
        assertTrue(Point3(1e-5, 0.0, 0.0).isNearZero())
        assertFalse(Point3(1e-3, 0.0, 0.0).isNearZero())
    }

    @Test
    fun `reflect and refract`() {
        val normal = Point3(0.0, 1.0, 0.0)
        assertPoint(Point3(1.0, 1.0, 0.0), Point3(1.0, -1.0, 0.0).reflect(normal))
        val straightDown = Point3(0.0, -1.0, 0.0)
        assertPoint(straightDown, straightDown.refract(normal, 1.0 / 1.5))
    }

    @Test
    fun `sphere hit returns nearest root with outward normal`() {
        val sphere = Sphere(Point3(0.0, 0.0, -5.0), 1.0, Lambert(Color(0.5, 0.5, 0.5)))
        val hit = assertNotNull(sphere.hit(Ray(Point3.ZERO, Point3(0.0, 0.0, -1.0)), 0.001, Double.POSITIVE_INFINITY))
        assertEquals(4.0, hit.t, eps)
        assertPoint(Point3(0.0, 0.0, -4.0), hit.p)
        assertPoint(Point3(0.0, 0.0, 1.0), hit.normal)
        assertTrue(hit.isFrontFace)
    }

    @Test
    fun `sphere miss and inside hit`() {
        val sphere = Sphere(Point3(0.0, 0.0, -5.0), 1.0, Lambert(Color(0.5, 0.5, 0.5)))
        assertNull(sphere.hit(Ray(Point3.ZERO, Point3(0.0, 1.0, 0.0)), 0.001, Double.POSITIVE_INFINITY))
        val inside = assertNotNull(
            sphere.hit(Ray(Point3(0.0, 0.0, -5.0), Point3(0.0, 0.0, -1.0)), 0.001, Double.POSITIVE_INFINITY)
        )
        assertEquals(1.0, inside.t, eps)
        assertFalse(inside.isFrontFace)
        assertPoint(Point3(0.0, 0.0, 1.0), inside.normal)
    }

    @Test
    fun `triangle hit inside and miss outside`() {
        val triangle = Triangle(
            Point3(-1.0, -1.0, -2.0), Point3(1.0, -1.0, -2.0), Point3(0.0, 1.0, -2.0),
            Lambert(Color(0.5, 0.5, 0.5))
        )
        val hit = assertNotNull(triangle.hit(Ray(Point3.ZERO, Point3(0.0, 0.0, -1.0)), 0.001, 100.0))
        assertEquals(2.0, hit.t, eps)
        assertPoint(Point3(0.0, 0.0, 1.0), hit.normal)
        assertNull(triangle.hit(Ray(Point3.ZERO, Point3(5.0, 0.0, -1.0)), 0.001, 100.0))
        assertNull(triangle.hit(Ray(Point3.ZERO, Point3(1.0, 0.0, 0.0)), 0.001, 100.0))
    }

    @Test
    fun `a triangle with a subnormal determinant keeps finite hit parameters`() {
        val triangle = Triangle(Point3.ZERO, Point3(1, 0, 0), Point3(0, 1, 0), Lambert(Color.ONE))
        val direction = Point3(0.0, 0.0, -1e-310)
        val inside = Ray(Point3(0.25, 0.25, 1e-310), direction)
        val corner = Ray(Point3(0.0, 0.0, 1.0), direction)
        assertEquals(1.0, assertNotNull(triangle.hit(inside, 0.0, 2.0)).t)
        assertNull(triangle.hit(corner, 0.0, Double.POSITIVE_INFINITY))
    }

    @Test
    fun `world hit picks the closest object`() {
        val world = World()
        world.add(Sphere(Point3(0.0, 0.0, -10.0), 1.0, Lambert(Color(0.1, 0.1, 0.1))))
        world.add(Sphere(Point3(0.0, 0.0, -3.0), 1.0, Lambert(Color(0.2, 0.2, 0.2))))
        val hit = assertNotNull(world.hit(Ray(Point3.ZERO, Point3(0.0, 0.0, -1.0)), 0.001, Double.POSITIVE_INFINITY))
        assertEquals(2.0, hit.t, eps)
    }

    @Test
    fun `world translates object by id`() {
        val world = World()
        world.add(Sphere(Point3(0.0, 0.0, -3.0), 1.0, Lambert(Color(0.2, 0.2, 0.2)), id = "S"))
        assertEquals(listOf("S"), world.getObjectIds())
        world.translateObjectById("S", Point3(0.0, 0.0, -1.0))
        val hit = assertNotNull(world.hit(Ray(Point3.ZERO, Point3(0.0, 0.0, -1.0)), 0.001, Double.POSITIVE_INFINITY))
        assertEquals(3.0, hit.t, eps)
        assertEquals(listOf("S"), world.getObjectIds())
    }

    @Test
    fun `translate returns a moved copy and keeps the original`() {
        val original = Triangle(Point3(0.0, 0.0, 0.0), Point3(1.0, 0.0, 0.0), Point3(0.0, 1.0, 0.0), Dielectric(1.5), id = "T")
        val moved = original.translate(Point3(1.0, 1.0, 1.0))
        assertEquals("T", moved.id)
        assertEquals(LineSegment(Point3(0.0, 0.0, 0.0), Point3(1.0, 0.0, 0.0)), original.linearize(1.0).first())
        assertEquals(LineSegment(Point3(1.0, 1.0, 1.0), Point3(2.0, 1.0, 1.0)), moved.linearize(1.0).first())
    }

    @Test
    fun `ray tracer returns sky gradient on miss`() {
        val tracer = RayTracer(World(), 5)
        assertPoint(Color(0.5, 0.7, 1.0), tracer.color(Ray(Point3.ZERO, Point3(0.0, 1.0, 0.0)), Random(0)))
        assertPoint(Color(1.0, 1.0, 1.0), tracer.color(Ray(Point3.ZERO, Point3(0.0, -1.0, 0.0)), Random(0)))
        val mid = 0.5 * (1 / sqrt(2.0) + 1.0)
        assertPoint(
            Color(1.0, 1.0, 1.0) * (1.0 - mid) + Color(0.5, 0.7, 1.0) * mid,
            tracer.color(Ray(Point3.ZERO, Point3(1.0, 1.0, 0.0)), Random(0))
        )
    }

    @Test
    fun `ray tracer returns emitted color of emissive object`() {
        val world = World()
        world.add(Sphere(Point3(0.0, 0.0, -3.0), 1.0, Emissive(Color(0.1, 0.7, 0.4), 2.0)))
        val tracer = RayTracer(world, 5)
        assertPoint(Color(0.2, 1.4, 0.8), tracer.color(Ray(Point3.ZERO, Point3(0.0, 0.0, -1.0)), Random(0)))
        assertPoint(Point3.ZERO, RayTracer(world, 0).color(Ray(Point3.ZERO, Point3(0.0, 0.0, -1.0)), Random(0)))
    }

    @Test
    fun `color to awt applies gamma and clamps`() {
        assertEquals(java.awt.Color(0, 0, 0).rgb, Color(0.0, 0.0, 0.0).toAWTColor(1))
        assertEquals(java.awt.Color(255, 255, 255).rgb, Color(5.0, 5.0, 5.0).toAWTColor(1))
        assertEquals(java.awt.Color(128, 128, 128).rgb, Color(1.0, 1.0, 1.0).toAWTColor(4))
    }
}
