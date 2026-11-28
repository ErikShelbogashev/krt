package krt.domain.scene.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import krt.domain.scene.World
import krt.i9ns.scenefile.SceneLibrary
import krt.platform.math.Color
import krt.platform.math.Point3

class SceneCatalogTest {

    @Test
    fun `the triangles of one mesh are one object in the list, at the centre of them all`() {
        val glass = Dielectric(1.5)
        val prism = listOf(
            Triangle(Point3.ZERO, Point3(2.0, 0.0, 0.0), Point3(0.0, 2.0, 0.0), glass, "Prism"),
            Triangle(Point3(0.0, 0.0, 2.0), Point3(2.0, 0.0, 2.0), Point3(0.0, 2.0, 2.0), glass, "Prism"),
        )
        val catalog = SceneCatalog.of(prism + Sphere(Point3(5.0, 0.0, 0.0), 1.0, glass, "Ball"))
        assertEquals(listOf("Prism", "Ball"), catalog.featured.map { it.id })
        assertEquals(Point3(1.0, 1.0, 1.0), catalog.featured.first().position)
    }

    @Test
    fun `every row of the list of every built-in scene has a key of its own`() {
        val library = SceneLibrary()
        DemoScene.entries.forEach { scene ->
            val catalog = SceneCatalog.of(library.load(scene).objects)
            val keys = catalog.featured.map { it.id } + catalog.groups.flatMap { group ->
                listOf("group:${group.title}") + group.objects.map { it.id }
            }
            val repeated = keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            assertTrue(repeated.isEmpty(), "$scene repeats keys ${repeated.take(5)}")
        }
    }

    @Test
    fun `large and named objects are listed one by one, the rest grouped by kind`() {
        val objects = listOf(
            Sphere(Point3(0.0, 1.0, 0.0), 1.0, Metal(Color(0.7, 0.6, 0.5), 0.0)),
            Sphere(Point3(1.0, 0.2, 0.0), 0.2, Lambert(Color(0.1, 0.2, 0.3))),
            Sphere(Point3(2.0, 0.2, 0.0), 0.2, Dielectric(1.5), id = "TEST"),
            Triangle(Point3.ZERO, Point3(1.0, 0.0, 0.0), Point3(0.0, 1.0, 0.0), Emissive(Color(1.0, 1.0, 1.0), 2.0)),
        )

        val catalog = SceneCatalog.of(objects)

        assertEquals(listOf("Sphere · Metal", "TEST"), catalog.featured.map { it.name })
        assertEquals(listOf("Small spheres" to 1, "Triangles" to 1), catalog.groups.map { it.title to it.objects.size })
        assertEquals(4, catalog.size)
    }

    @Test
    fun `summary shows the material color and the center of the object`() {
        val sphere = Sphere(Point3(4.0, 1.0, 0.0), 1.0, Lambert(Color(0.4, 0.2, 0.1)))
        val summary = SceneCatalog.summary(sphere)
        assertEquals(Color(0.4, 0.2, 0.1), summary.color)
        assertEquals(Point3(4.0, 1.0, 0.0), summary.position)
        assertEquals(sphere.id, summary.id)
    }

    @Test
    fun `objects are found by id among the featured ones and in groups`() {
        val catalog = SceneCatalog.of(World.default(Random(1)).objects)
        assertEquals("TEST", catalog.find("TEST")?.name)
        val grouped = catalog.groups.first().objects.first()
        assertEquals(grouped, catalog.find(grouped.id))
        assertNull(catalog.find(null))
    }

    @Test
    fun `the default scene lists its five large spheres and groups the small ones`() {
        val world = World.default(Random(1))
        val catalog = SceneCatalog.of(world.objects)
        assertEquals(5, catalog.featured.size)
        assertEquals(listOf("Small spheres"), catalog.groups.map { it.title })
        assertEquals(world.objects.size, catalog.size)
    }
}
