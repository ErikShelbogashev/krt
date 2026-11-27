package krt.domain.scene.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

class FractalTest {

    private val grey = Lambert(Color(0.5, 0.5, 0.5))

    @Test
    fun `a ray meets a face of the sponge where the cube is solid`() {
        val sponge = Fractal(FractalKind.MENGER, Point3.ZERO, 1.75, grey)
        val hit = assertNotNull(sponge.hit(Ray(Point3(0.95, 0.95, 5.0), Point3(0.0, 0.0, -1.0)), 0.001, 100.0))
        assertEquals(4.0, hit.t, 1e-3)
        assertEquals(1.0, hit.normal.z, 1e-2)
    }

    @Test
    fun `a ray through the middle hole of the sponge passes`() {
        val sponge = Fractal(FractalKind.MENGER, Point3.ZERO, 1.75, grey)
        assertNull(sponge.hit(Ray(Point3(0.0, 0.0, 5.0), Point3(0.0, 0.0, -1.0)), 0.001, 100.0))
    }

    @Test
    fun `every fractal stops a ray at its heart, inside its ball, and moves as one`() {
        FractalKind.entries.forEach { kind ->
            val fractal = Fractal(kind, Point3(1.0, 2.0, 3.0), 2.0, grey)
            val ray = Ray(Point3(1.0, 2.0, 13.0), Point3(0.05, 0.03, -1.0).unit())
            val hit = assertNotNull(fractal.hit(ray, 0.001, 100.0), "$kind")
            assertTrue((hit.p - fractal.center).length() < 2.0 + 1e-6, "$kind")
            assertTrue(fractal.distance(hit.p) < 2.0 * Fractal.SURFACE, "$kind")
            assertEquals(1.0, hit.normal.length(), 1e-9)
            val moved = fractal.translate(Point3(1.0, 0.0, 0.0))
            assertEquals(fractal.distance(hit.p), moved.distance(hit.p + Point3(1.0, 0.0, 0.0)), 1e-9)
        }
    }

    @Test
    fun `distance estimates do not overshoot the surface`() {
        val bulb = Fractal(FractalKind.MANDELBULB, Point3.ZERO, 1.25, grey)
        val ray = Ray(Point3(0.0, 0.0, 3.0), Point3(0.0, 0.0, -1.0))
        val hit = assertNotNull(bulb.hit(ray, 0.001, 100.0))
        val fine = (0 until 3000).map { 3.0 - it * 0.001 }.first { bulb.distance(Point3(0.0, 0.0, it)) < 1e-4 }
        assertEquals(3.0 - fine, hit.t, 2e-3)
    }
}
