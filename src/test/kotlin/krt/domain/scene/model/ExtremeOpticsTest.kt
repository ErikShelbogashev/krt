package krt.domain.scene.model

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import krt.platform.math.Point3
import krt.platform.math.Ray

class ExtremeOpticsTest {
    private val yWeights = Point3(0.2126, 0.7152, 0.0722)

    @Test
    fun `cold normalized black bodies converge to the longest visible quadrature wavelength`() {
        val cold = Spectrum.weight(717.5)
        val limit = cold / cold.dot(yWeights)
        for (temperature in listOf(Double.MIN_VALUE, 1.0)) {
            val rgb = Blackbody.color(temperature)
            assertEquals(1.0, rgb.dot(yWeights), 1e-14)
            for (channel in 0..2) assertEquals(limit[channel], rgb[channel], 1e-7)
        }
        // Direct Planck quadrature with 80 decimal digits at 20 K remains representable in the reference.
        val measured = Blackbody.color(20.0)
        assertEquals(4.702870909, measured.x, 1e-8)
        assertEquals(0.00023719902, measured.y, 1e-10)
        assertEquals(Point3.ZERO, Blackbody.color(0.0))
    }

    @Test
    fun `hot normalized black bodies converge to the Rayleigh Jeans spectrum`() {
        // For hc/(λkT) << 1, Planck radiance is proportional to T/λ⁴.
        val integral = (0 until 68).fold(Point3.ZERO) { sum, i ->
            val nm = 382.5 + i * 5.0
            sum + Spectrum.weight(nm) / nm.pow(4)
        }
        val limit = integral / integral.dot(yWeights)
        for (temperature in listOf(1e25, Double.MAX_VALUE)) {
            val rgb = Blackbody.color(temperature)
            for (channel in 0..2) assertEquals(limit[channel], rgb[channel], 1e-12)
        }
    }

    @Test
    fun `rescaling extreme finite directions preserves sphere and fractal hits and interval clipping`() {
        val z = Point3(0, 0, 1)
        val matte = Lambert(Point3.ONE)
        val shapes = listOf(Sphere(z * 2.0, 1.0, matte),
            Fractal(FractalKind.JULIA, z * 2.0, 1.0, matte, seed = Point3.ZERO))
        for (shape in shapes) {
            val reference = assertNotNull(shape.hit(Ray(Point3.ZERO, z), 0.0, 4.0))
            for (scale in listOf(1e-200, 1e200)) {
                val ray = Ray(Point3.ZERO, z * scale)
                val hit = assertNotNull(shape.hit(ray, 0.0, 4.0 / scale))
                assertEquals(reference.t, hit.t * scale, 1e-12)
                assertEquals(reference.p.z, hit.p.z, 1e-12)
                assertNull(shape.hit(ray, 0.0, reference.t / scale * 0.9))
            }
        }
    }
}
