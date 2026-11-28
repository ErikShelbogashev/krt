package krt.domain.render

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import krt.domain.camera.CameraView
import krt.domain.scene.World
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.Fractal
import krt.domain.scene.model.FractalKind
import krt.domain.scene.model.ObjectChange
import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Sky
import krt.domain.scene.model.StartView
import krt.i9ns.pbrt.PbrtReader
import krt.i9ns.pbrt.PbrtWriter
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

class FractalDetailTest {
    private val red = Color(1.0, 0.0, 0.0)
    private val look = SceneLook.CLASSIC.copy(sky = Sky(Color.ZERO, Color.ZERO))
    private val eye = Point3(2.0 / 3, 2.0 / 3, 5.0)
    private val forward = Point3(0.0, 0.0, -1.0)

    @Test
    fun `a second level hole opens on both renderers and stays open after saving`() {
        // The outer corner square has its own axial tunnel at (2/3, 2/3) only from level two.
        val sponge = Fractal(FractalKind.MENGER, Point3.ZERO, 1.75, Emissive(red, 1.0), "edited", iterations = 1)
        val other = Fractal(FractalKind.MENGER, Point3(3, 0, 0), 1.75, sponge.material, "untouched", iterations = 1)
        val world = World(listOf(sponge, other), look)
        assertRayColor(world, red, 0xff0000)
        world.editObject(sponge.id, ObjectChange.Iterations(2))
        assertSame(other, world.objects.last())
        assertRayColor(world, Color.ZERO, 0)
        val file = PbrtWriter.write(world.objects, world.look, StartView(eye, eye + forward), "fractals")
        val read = PbrtReader.read(file.text, file.files)
        assertTrue(read.warnings.isEmpty())
        val restored = read.objects.map { assertIs<Fractal>(it).run { id to iterations } }
        assertEquals(listOf("edited" to 2, "untouched" to 1), restored)
        assertRayColor(World(read.objects, read.look), Color.ZERO, 0)
    }

    @Test
    fun `direct construction and edits use the supported finite range for each fractal`() {
        for (kind in FractalKind.entries) {
            val fractal = Fractal(kind, Point3.ZERO, 2.0, Emissive(red, 1.0), iterations = Int.MAX_VALUE)
            assertEquals(if (kind == FractalKind.MENGER) 8 else 16, fractal.iterations)
            assertSame(fractal, fractal.withIterations(Int.MAX_VALUE))
            val edited = fractal.withIterations(Int.MIN_VALUE)
            assertEquals(1, edited.iterations)
            assertEquals(fractal.id, edited.id)
            assertSame(fractal.material, edited.material)
            assertEquals(fractal.kind, edited.kind)
            assertEquals(fractal.parameter, edited.parameter)
            assertEquals(fractal.seed, edited.seed)
        }
    }

    private fun assertRayColor(world: World, expected: Color, rgb: Int) {
        assertEquals(expected, RayTracer(world, 2).color(Ray(eye, forward), Random(2)))
        val view = CameraView(eye, eye + forward, Point3.ZERO, Point3.ZERO, Point3.ZERO, Point3.ZERO, 0.0)
        val pixels = rasterPixels(GpuFrame(world.gpuScene, view, 2, 2, 2, 1, 0.5f))
        assertTrue(pixels.all { it and 0xffffff == rgb }, "SkSL rendered ${pixels.toList()} instead of RGB $rgb")
    }
}
