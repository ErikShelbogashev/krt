package raytracer.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
}
