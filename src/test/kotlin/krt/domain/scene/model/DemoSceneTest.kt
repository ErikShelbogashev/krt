package krt.domain.scene.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import krt.platform.math.Point3

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
    fun `the materials scene shows every physical material, lit by sampled lights and with motion blur`() {
        val objects = DemoScene.MATERIALS.objects()
        val materials = objects.mapNotNull {
            when (it) {
                is Sphere -> it.material
                is Triangle -> it.material
                else -> null
            }
        }.map { it::class }.toSet()
        val expected = setOf(
            Conductor::class, Dielectric::class, SoapBubble::class, Iridescent::class, Velvet::class,
            Patterned::class, GlowingPattern::class, Emissive::class, Plastic::class,
        )
        assertTrue(materials.containsAll(expected), "missing ${expected - materials}")
        assertTrue(objects.any { it is Translucent } && objects.any { it is Fog })
        assertTrue(objects.filterIsInstance<Triangle>().any { it.normals != null })
        assertTrue(objects.filterIsInstance<Sphere>().any { it.velocity != Point3.ZERO })
        assertTrue(DemoScene.MATERIALS.look.sampleLights && DemoScene.MATERIALS.look.motionBlur)
        assertEquals(DemoScene.MATERIALS, DemoScene.showing(DemoScene.MATERIALS.look))
    }

    @Test
    fun `each scene has its own look`() {
        assertEquals(Sky.DAYLIGHT, DemoScene.CLASSIC.look.sky)
        assertTrue(DemoScene.SHOWCASE.look.fov < DemoScene.CLASSIC.look.fov)
    }

    @Test
    fun `a look tells which demo scene is shown`() {
        assertEquals(DemoScene.SHOWCASE, DemoScene.showing(DemoScene.SHOWCASE.look))
        assertEquals(null, DemoScene.showing(SceneLook(Sky.DAYLIGHT, fov = 12.0, aperture = 0.0)))
    }
}
