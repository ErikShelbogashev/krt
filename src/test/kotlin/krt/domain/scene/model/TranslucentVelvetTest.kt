package krt.domain.scene.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

class TranslucentVelvetTest {

    private val jade = Translucent(Point3.ZERO, 1.0, Color(0.4, 0.9, 0.6), density = 50.0)

    @Test
    fun `light enters a translucent ball through its surface`() {
        val hit = assertNotNull(jade.hit(Ray(Point3(0.0, 0.0, -5.0), Point3(0.0, 0.0, 1.0)), 0.001, 100.0))
        assertEquals(4.0, hit.t, 1e-9)
        assertIs<Dielectric>(hit.material)
    }

    @Test
    fun `inside a dense ball light scatters off particles before it reaches the surface`() {
        val inside = (0 until 100).mapNotNull { i ->
            jade.hit(Ray(Point3(0.0, i * 1e-4, -0.999), Point3(0.0, 0.0, 1.0)), 0.001, 100.0)
        }
        assertTrue(inside.count { it.material is Isotropic } > 90)
        assertTrue(inside.all { it.t < 2.0 })
        assertEquals(listOf(jade.surface, jade.inside), jade.parts)
    }

    @Test
    fun `a translucent ball moves as one`() {
        val moved = jade.translate(Point3(1.0, 0.0, 0.0))
        assertEquals(Point3(1.0, 0.0, 0.0), moved.surface.center)
        assertEquals(Point3(1.0, 0.0, 0.0), moved.inside.center)
        assertEquals(jade.id, moved.id)
    }

    @Test
    fun `velvet glows at grazing angles`() {
        val normal = Point3(0.0, 1.0, 0.0)
        val headOn = averageSheen(Point3(0.0, 1.0, 0.0), normal)
        val grazing = averageSheen(Point3(1.0, 0.08, 0.0).unit(), normal)
        assertTrue(grazing > 2 * headOn, "grazing $grazing vs head on $headOn")
    }

    @Test
    fun `velvet picks its bounces by the brightness of base and sheen`() {
        val velvet = Velvet(Color(0.3, 0.0, 0.1), Color(0.6, 0.6, 0.6), roughness = 0.4)
        val ray = Ray(Point3(0.0, 1.0, 0.0), Point3(0.0, -1.0, 0.0))
        val hit = Hit.create(Point3.ZERO, 1.0, ray, Point3(0.0, 1.0, 0.0), velvet)
        val lobes = (0 until 1000).map { velvet.scatter(ray, hit, Random(it)).lobe }
        val diffuse = lobes.count { it == Lobe.DIFFUSE } / 1000.0
        assertEquals(velvet.diffuseChance, diffuse, 0.05)
        assertEquals(0.4 / 2.2, velvet.diffuseChance, 1e-9)
    }

    private fun averageSheen(toViewer: Point3, normal: Point3): Double {
        val random = Random(5)
        return (0 until 5000).sumOf {
            val direction = (normal + Point3.randomInUnitSphere(random)).unit()
            Sheen.weight(toViewer, direction, normal, roughness = 0.5)
        } / 5000
    }
}
