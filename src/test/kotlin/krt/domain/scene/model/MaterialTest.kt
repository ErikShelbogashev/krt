package krt.domain.scene.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

class MaterialTest {

    private val ray = Ray(Point3(0.0, 1.0, 0.0), Point3(0.0, -1.0, 0.0))
    private fun hit(material: Material) =
        Hit.create(Point3(0.0, 0.0, 0.0), 1.0, ray, Point3(0.0, 1.0, 0.0), material)

    @Test
    fun `scatter is deterministic for a given random`() {
        listOf(Lambert(Color(0.5, 0.5, 0.5)), Metal(Color(0.7, 0.6, 0.5), 0.3), Dielectric(1.5)).forEach { material ->
            val a = material.scatter(ray, hit(material), Random(3))
            val b = material.scatter(ray, hit(material), Random(3))
            assertEquals(a?.ray?.direction, b?.ray?.direction, material::class.simpleName)
        }
    }

    @Test
    fun `lambert scatters into the hemisphere around the normal`() {
        val material = Lambert(Color(0.5, 0.5, 0.5))
        repeat(100) { seed ->
            val scattered = material.scatter(ray, hit(material), Random(seed))
            assertTrue(scattered.ray.direction.y > 0.0)
            assertEquals(Color(0.5, 0.5, 0.5), scattered.attenuation)
        }
    }

    @Test
    fun `polished metal reflects mirror-like`() {
        val material = Metal(Color(0.7, 0.6, 0.5), 0.0)
        val scattered = assertNotNull(material.scatter(ray, hit(material), Random(0)))
        assertEquals(Point3(0.0, 1.0, 0.0), scattered.ray.direction)
    }

    @Test
    fun `emissive does not scatter`() {
        val material = Emissive(Color(0.1, 0.7, 0.4), 2.0)
        assertNull(material.scatter(ray, hit(material), Random(0)))
    }

    @Test
    fun `clear coat reflects more at grazing angles`() {
        assertEquals(0.04, ClearCoat.reflectance(1.0, 1.5), 1e-9)
        assertEquals(1.0, ClearCoat.reflectance(0.0, 1.5), 1e-9)
    }

    @Test
    fun `plastic reflects white off its coat or scatters its albedo from the base`() {
        val plastic = Plastic(Color(0.8, 0.1, 0.1), roughness = 0.0)
        val attenuations = (0 until 2000).mapNotNull { seed ->
            plastic.scatter(ray, hit(plastic), Random(seed))?.attenuation
        }.toSet()
        assertEquals(setOf(Color(1.0, 1.0, 1.0), Color(0.8, 0.1, 0.1)), attenuations)
    }

    @Test
    fun `checker alternates its colours from cell to cell`() {
        val checker = Checker(Color(1.0, 1.0, 1.0), Color(0.0, 0.0, 0.0), cellSize = 1.0, roughness = 0.0)
        assertEquals(Color(1.0, 1.0, 1.0), checker.colorAt(Point3(0.5, 0.5, 0.5)))
        assertEquals(Color(0.0, 0.0, 0.0), checker.colorAt(Point3(1.5, 0.5, 0.5)))
        assertEquals(Color(0.0, 0.0, 0.0), checker.colorAt(Point3(-0.5, 0.5, 0.5)))
    }

    @Test
    fun `tinted glass darkens light by the distance it travelled inside`() {
        val glass = Dielectric(1.5, tint = Color(0.5, 1.0, 1.0), density = 1.0)
        val inside = Ray(Point3(0.0, 0.0, 0.0), Point3(0.0, 1.0, 0.0))
        val exit = Hit.create(Point3(0.0, 2.0, 0.0), 2.0, inside, Point3(0.0, 1.0, 0.0), glass)
        val attenuation = glass.scatter(inside, exit, Random(1)).attenuation
        assertEquals(0.25, attenuation.x, 1e-9)
        assertEquals(1.0, attenuation.y, 1e-9)
        assertEquals(Point3.ONE, glass.scatter(ray, hit(glass), Random(1)).attenuation)
    }

    @Test
    fun `clear glass keeps all light`() {
        val glass = Dielectric(1.5)
        assertEquals(Point3.ZERO, glass.absorption)
    }
}
