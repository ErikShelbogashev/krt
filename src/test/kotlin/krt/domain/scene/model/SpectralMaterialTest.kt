package krt.domain.scene.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

class SpectralMaterialTest {

    private val slanted = Ray(Point3(-1.0, 1.0, 0.0), Point3(1.0, -1.0, 0.0))

    private fun hit(ray: Ray, material: Material) = Hit.create(Point3.ZERO, 1.0, ray, Point3(0.0, 1.0, 0.0), material)

    @Test
    fun `the weights of all wavelengths average to white`() {
        val steps = 10_000
        val sum = (0 until steps).fold(Point3.ZERO) { acc, i ->
            acc + Spectrum.weight(Spectrum.sample((i + 0.5) / steps))
        } / steps.toDouble()
        listOf(sum.x, sum.y, sum.z).forEach { assertEquals(1.0, it, 1e-3) }
    }

    @Test
    fun `dispersive glass bends blue more than red`() {
        val glass = Dielectric(1.5, dispersion = 0.05)
        assertTrue(glass.indexAt(Spectrum.BLUE_NM) > glass.indexAt(Spectrum.RED_NM))
        assertEquals(1.5, glass.indexAt(0.0))
        assertEquals(1.5, Dielectric(1.5).indexAt(Spectrum.BLUE_NM))
    }

    @Test
    fun `white light through dispersive glass goes on as one weighted wavelength`() {
        val glass = Dielectric(1.5, dispersion = 0.05)
        val scattered = assertNotNull(glass.scatter(slanted, hit(slanted, glass), Random(4)))
        val wavelength = scattered.ray.wavelength
        assertTrue(wavelength in Spectrum.MIN_NM..Spectrum.MAX_NM)
        assertEquals(Spectrum.weight(wavelength), scattered.attenuation)

        val red = Ray(slanted.origin, slanted.direction, wavelength = Spectrum.RED_NM)
        val blue = Ray(slanted.origin, slanted.direction, wavelength = Spectrum.BLUE_NM)
        val redOut = refracted(glass, red)
        val blueOut = refracted(glass, blue)
        assertEquals(Spectrum.RED_NM, redOut.wavelength)
        assertNotEquals(redOut.direction, blueOut.direction)
    }

    @Test
    fun `glass without dispersion leaves white light white`() {
        val scattered = assertNotNull(Dielectric(1.5).scatter(slanted, hit(slanted, Dielectric(1.5)), Random(4)))
        assertEquals(0.0, scattered.ray.wavelength)
        assertEquals(Point3.ONE, scattered.attenuation)
    }

    @Test
    fun `a film of no thickness reflects as the bare substrate`() {
        assertEquals(0.0, ThinFilm.at(1.0, 0.0, 1.33, 1.0, Spectrum.GREEN_NM), 1e-9)
        assertEquals(0.04, ThinFilm.at(1.0, 0.0, 1.8, 1.5, Spectrum.GREEN_NM), 1e-9)
    }

    @Test
    fun `a soap film reflects colours that change with thickness and angle`() {
        val normal = (300..700 step 50).map { ThinFilm.at(1.0, it.toDouble(), 1.33, 1.0, Spectrum.GREEN_NM) }
        assertTrue(normal.all { it in 0.0..0.2 })
        assertTrue(normal.max() - normal.min() > 0.05, "$normal")
        val head = ThinFilm.at(1.0, 400.0, 1.33, 1.0, Spectrum.RED_NM)
        val grazing = ThinFilm.at(0.3, 400.0, 1.33, 1.0, Spectrum.RED_NM)
        assertNotEquals(head, grazing)
    }

    @Test
    fun `a bubble passes or reflects light, weighted to keep it unbiased`() {
        val bubble = SoapBubble(thickness = 380.0)
        val runs = 20_000
        val mean = (0 until runs).fold(Point3.ZERO) { acc, seed ->
            acc + bubble.scatter(slanted, hit(slanted, bubble), Random(seed)).attenuation
        } / runs.toDouble()
        listOf(mean.x, mean.y, mean.z).forEach { assertEquals(1.0, it, 0.05) }
    }

    @Test
    fun `an iridescent coat lights its base with what it does not reflect`() {
        val coat = Iridescent(Color(0.05, 0.05, 0.05), thickness = 500.0, filmIor = 1.8)
        val diffuse = (0 until 200).mapNotNull { coat.scatter(slanted, hit(slanted, coat), Random(it)) }
            .filter { it.lobe == Lobe.DIFFUSE }
        assertTrue(diffuse.isNotEmpty())
        assertTrue(diffuse.all { it.ray.direction.y > 0 })
    }

    private fun refracted(glass: Dielectric, ray: Ray): Ray =
        (0 until 50).mapNotNull { glass.scatter(ray, hit(ray, glass), Random(it))?.ray }.first { it.direction.y < 0 }
}
