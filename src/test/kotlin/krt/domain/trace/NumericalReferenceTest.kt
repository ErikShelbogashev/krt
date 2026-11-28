package krt.domain.trace

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.FractalDistance
import krt.domain.scene.model.FreeFlight
import krt.domain.scene.model.HenyeyGreenstein
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Medium
import krt.domain.scene.model.Sphere
import krt.domain.scene.model.Triangle
import krt.domain.scene.model.VertexNormals
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

class NumericalReferenceTest {
    private val white = Lambert(Color.ONE)
    private val z = Point3(0, 0, 1)

    @Test
    fun `small distant spheres retain the analytic chord and reject near misses`() {
        val sphere = Sphere(Point3(0, 0, 1e9), 1.0, white)
        val ray = Ray(Point3(0.75, 0.0, 0.0), z * 2.0)
        // Half-chord sqrt(1 − (3/4)²) evaluated with 34 decimal digits.
        val halfChord = BigDecimal("0.4375").sqrt(MathContext.DECIMAL128)
        val t = (BigDecimal("1000000000") - halfChord).divide(BigDecimal(2), MathContext.DECIMAL128)
        val hit = assertNotNull(sphere.hit(ray, 0.0, 1e9))
        assertEquals(t.toDouble(), hit.t, Math.ulp(t.toDouble()))
        assertNull(sphere.hit(Ray(Point3(0.8, 0.8, 0.0), z), 0.0, 2e9))
        assertNull(sphere.hit(Ray(Point3.ZERO, Point3.ZERO), 0.0, 2e9))
    }

    @Test
    fun `cancelling vertex normals preserve the triangle's geometric normal`() {
        val triangle = Triangle(Point3.ZERO, Point3(1, 0, 0), Point3(0, 1, 0), white, normals = VertexNormals(z, -z, z))
        val hit = assertNotNull(triangle.hit(Ray(Point3(0.5, 0.0, 1.0), -z), 0.0, 10.0))
        assertEquals(z, hit.normal)
    }

    @Test
    fun `a zero area lamp has zero probability and leaves the other light's density unchanged`() {
        val glow = Emissive(Color.ONE, 1.0)
        val degenerate = Triangle(Point3.ZERO, z, z * 2.0, glow, id = "line")
        val lamp = Triangle(z, Point3(1, 0, 1), Point3(0, 1, 1), glow, id = "lamp")
        val lights = Lights(listOf(degenerate, lamp))
        assertEquals(1, lights.count)
        val sample = assertNotNull(lights.sample(Point3.ZERO, 0.0, Random(7)))
        // Triangle area = 1/2, p_omega = r² / (A cos theta).
        assertEquals(2 * sample.distance * sample.distance / sample.direction.z, sample.pdf, 1e-12)
        assertEquals(1, GpuScenePacking.pack(listOf(degenerate, lamp)).sections.lightCount)
    }

    @Test
    fun `zero extinction leaves transparent wavelengths at infinite optical distance`() {
        val medium = Medium(Color(0.0, 0.0, 2.0), Color.ZERO)
        assertEquals(Color(1, 1, 0), medium.transmittance(Double.POSITIVE_INFINITY))
        assertEquals(Color(1.5, 1.5, 0), FreeFlight.passWeight(medium, Double.POSITIVE_INFINITY))
    }

    @Test
    fun `spectral mixture probabilities survive overflow and gradual underflow`() {
        val tint = Color(0.5, 0.25, 0.125)
        for (density in listOf(Double.MAX_VALUE, Double.MIN_VALUE)) {
            val grey = Medium.grey(density, tint)
            assertEquals(tint, FreeFlight.scatterWeight(grey, 0.0))
        }
        // exp(-745) is the smallest subnormal. The other channels underflow completely.
        val medium = Medium(Color(1, 2, 3), Color.ONE)
        val pass = FreeFlight.passWeight(medium, 745.0)
        assertEquals(3.0, pass.x)
        assertTrue(pass.y <= 3 * Double.MIN_VALUE && pass.z == 0.0)
        assertEquals(Color(3, 0, 0), FreeFlight.scatterWeight(medium, 800.0))
        assertEquals(Color.ONE, FreeFlight.passWeight(Medium.grey(1.0, tint), 800.0))
    }

    @Test
    fun `Henyey Greenstein retains its pole density and small nonzero first moment`() {
        val g = 1.0 - Math.scalb(1.0, -30)
        val expected = (1 + g) / (4 * PI * (1 - g) * (1 - g))
        assertEquals(expected, HenyeyGreenstein.density(1.0, g), expected * 1e-14)
        assertEquals(expected, HenyeyGreenstein.density(-1.0, -g), expected * 1e-14)
        val mild = 0.0005
        val bins = 16384
        val moment = (0 until bins).sumOf { HenyeyGreenstein.sample(z, mild, (it + 0.5) / bins, 0.0).z } / bins
        assertEquals(mild, moment, 1e-10)
    }

    @Test
    fun `zero seed quaternion Julia has the exact signed distance to a unit ball`() {
        for (x in listOf(0.0, 0.5, 1.0, 2.0)) {
            assertEquals(x - 1.0, FractalDistance.julia(x, 0.0, 0.0, 11, Point3.ZERO))
        }
    }

    @Test
    fun `a vector can have a finite unit direction across the range of doubles`() {
        for (scale in listOf(Double.MIN_VALUE, 1e-200, 1e200, Double.MAX_VALUE * 0.75)) {
            val v = Point3(scale, scale, scale).unit()
            assertEquals(1.0, v.length(), 3e-15)
            assertEquals(1.0 / kotlin.math.sqrt(3.0), v.z, 1e-15)
        }
        val tiny = Point3(3e-200, 4e-200, 0.0).length()
        assertTrue(abs(tiny / 5e-200 - 1.0) < 1e-15)
    }

    @Test
    fun `the Mandelbulb's critical fixed point has zero marching distance`() {
        assertEquals(0.0, FractalDistance.mandelbulb(0.0, 0.0, 0.0, 10, 8.0))
    }
}
