package krt.domain.scene.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DemoSceneTest {

    @Test
    fun `the showcase has its named pieces, meshes and lights`() {
        val objects = DemoScene.SHOWCASE.objects(Random(1))
        val names = objects.map { it.id }
        assertTrue(listOf("Floor", "Mirror ball", "Red plastic", "White plastic").all { it in names })
        val triangles = objects.filterIsInstance<Triangle>()
        assertEquals(200 * 14 * 2 + 64 * 16 * 2 + 80 + 4, triangles.size)
        assertEquals(4, triangles.count { it.material is Emissive })
    }

    @Test
    fun `the same randomness gives the same showcase`() {
        val first = DemoScene.SHOWCASE.objects(Random(3)).filterIsInstance<Sphere>().map { it.center }
        val second = DemoScene.SHOWCASE.objects(Random(3)).filterIsInstance<Sphere>().map { it.center }
        assertEquals(first, second)
    }

    @Test
    fun `small spheres lie on the floor`() {
        val small = DemoScene.SHOWCASE.objects(Random(1)).filterIsInstance<Sphere>().filter { it.radius < 0.31 }
        assertTrue(small.isNotEmpty())
        small.forEach { assertEquals(it.radius, it.center.y, 1e-9) }
    }

    @Test
    fun `each scene has its own look`() {
        assertEquals(Sky.DAYLIGHT, DemoScene.CLASSIC.look.sky)
        assertTrue(DemoScene.SHOWCASE.look.fov < DemoScene.CLASSIC.look.fov)
    }
}
