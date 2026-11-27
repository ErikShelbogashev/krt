package krt.i9ns.scenefile

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import krt.domain.scene.model.Conductor
import krt.domain.scene.model.DemoScene
import krt.domain.scene.model.DemoScenes
import krt.domain.scene.model.Dielectric
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.Fog
import krt.domain.scene.model.Fractal
import krt.domain.scene.model.FractalKind
import krt.domain.scene.model.GlowingPattern
import krt.domain.scene.model.Iridescent
import krt.domain.scene.model.Patterned
import krt.domain.scene.model.Plastic
import krt.domain.scene.model.SoapBubble
import krt.domain.scene.model.Sphere
import krt.domain.scene.model.Traceable
import krt.domain.scene.model.Triangle
import krt.domain.scene.model.Velvet
import krt.i9ns.pbrt.PbrtWriter
import krt.platform.math.Point3

class SceneLibraryTest {

    private val library = SceneLibrary()

    @Test
    fun `every demo scene reads without warnings`() {
        DemoScene.entries.forEach { scene ->
            val description = library.load(scene)
            assertTrue(description.objects.isNotEmpty(), "$scene")
            assertTrue(description.warnings.isEmpty(), "$scene: ${description.warnings}")
        }
    }

    @Test
    fun `the classic scene reads from its file exactly as the code lays it out`() {
        val classic = DemoScenes.classic(Random(1))
        val read = library.load(DemoScene.CLASSIC).objects
        assertEquals(classic.size, read.size)
        classic.zip(read).forEach { (a, b) ->
            a as Sphere
            b as Sphere
            assertEquals(a.center, b.center)
            assertEquals(a.radius, b.radius)
            assertEquals(PbrtWriter.material(a.material), PbrtWriter.material(b.material))
        }
    }

    @Test
    fun `the showcase has its named pieces, meshes and lights`() {
        val objects = library.load(DemoScene.SHOWCASE).objects
        val names = objects.map { it.id }
        assertTrue(listOf("Floor", "Mirror ball", "Red plastic", "White plastic").all { it in names })
        val triangles = objects.filterIsInstance<Triangle>()
        assertEquals(200 * 14 * 2 + 64 * 16 * 2 + 80 + 4, triangles.size)
        assertEquals(4, triangles.count { it.material is Emissive })
    }

    @Test
    fun `the materials scene shows every physical material, lit by sampled lights and with motion blur`() {
        val scene = library.load(DemoScene.MATERIALS)
        val kinds = materials(scene.objects)
        val expected = setOf(
            Conductor::class, Dielectric::class, SoapBubble::class, Iridescent::class, Velvet::class,
            Patterned::class, GlowingPattern::class, Emissive::class, Plastic::class,
        )
        assertTrue(kinds.containsAll(expected), "missing ${expected - kinds}")
        assertTrue(scene.objects.any { it is Fog })
        assertTrue(scene.objects.any { it is Sphere && it.media?.inside != null }, "translucent balls")
        assertTrue(scene.objects.filterIsInstance<Triangle>().any { it.normals != null })
        assertTrue(scene.objects.filterIsInstance<Sphere>().any { it.velocity != Point3.ZERO })
        assertTrue(scene.look.sampleLights && scene.look.motionBlur)
    }

    @Test
    fun `the fractals scene has every fractal`() {
        val fractals = library.load(DemoScene.FRACTALS).objects.filterIsInstance<Fractal>()
        assertEquals(FractalKind.entries.toSet(), fractals.map { it.kind }.toSet())
    }

    private fun materials(objects: List<Traceable>) = objects.mapNotNull {
        when (it) {
            is Sphere -> it.material
            is Triangle -> it.material
            else -> null
        }
    }.map { it::class }.toSet()
}
