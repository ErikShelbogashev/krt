package krt.domain.render

import kotlin.math.exp
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import krt.domain.camera.CameraView
import krt.domain.scene.World
import krt.domain.scene.model.Fog
import krt.domain.scene.model.Interface
import krt.domain.scene.model.Medium
import krt.domain.scene.model.MediumInterface
import krt.domain.scene.model.Meshes
import krt.domain.scene.model.ObjectChange
import krt.domain.scene.model.Pose
import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Sky
import krt.domain.scene.model.Sphere
import krt.domain.scene.model.Triangle
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

class NestedExtinctionEditTest {
    private val old = Medium(Color.ZERO, Color.ZERO)
    private val edit = ObjectChange.Extinction(ObjectChange.Channel.G, 0.25)

    @Test
    fun `light leaving an inner clear cavity attenuates in the edited outer medium`() {
        val outer = Fog(Point3.ZERO, 3.0, old, "haze")
        val cavity = Sphere(Point3.ZERO, 1.0, Interface, "cavity", media = MediumInterface(null, old))
        val sky = Color.ONE * 0.25
        val world = World(listOf(outer, cavity), SceneLook.CLASSIC.copy(sky = Sky(sky, sky)))
        world.editObject("haze", edit)
        val eye = Point3(0, 0, -4)
        val direction = Point3(0, 0, 1)
        val rng = Random(2)
        val tracer = RayTracer(world, 3)
        var cpu = Color.ZERO
        repeat(256) { cpu += tracer.color(Ray(eye, direction), rng) }
        // Two units before the cavity and two after it, with no absorption inside it.
        val expected = exp(-0.25 * 4)
        assertEquals(expected, cpu.y / cpu.x, 1e-7)
        val view = CameraView(eye, eye + direction, Point3.ZERO, Point3.ZERO, Point3.ZERO, Point3.ZERO, 0.0)
        val pixels = rasterPixels(GpuFrame(world.gpuScene, view, 4, 4, 3, 8, 0.5f))
        fun channel(shift: Int) = pixels.sumOf { val code = (it shr shift) and 255; code * code }.toDouble()
        assertEquals(expected, channel(8) / channel(16), 0.015)
    }

    @Test
    fun `equal media in disjoint and multiply nested closed regions remain independent`() {
        val box = Meshes.box(Pose(Point3.ZERO), Point3(4, 4, 4), Interface, MediumInterface(old)).map {
            Triangle(it.v0, it.v1, it.v2, it.material, "box", media = it.media)
        }
        val nested = Fog(Point3.ZERO, 2.0, old, "nested", old)
        val deep = Sphere(Point3.ZERO, 1.0, Interface, "deep", media = MediumInterface(null, old))
        val far = Fog(Point3(12, 0, 0), 3.0, old, "far")
        val remote = deep.translate(Point3(12, 0, 0))
        val world = World(box + listOf(nested, deep, far, remote))
        world.editObject("box", edit)
        val parts = world.objects.takeLast(4)
        val middle = parts[0] as Fog
        assertEquals(Color(0.0, 0.25, 0.0), middle.outside?.density)
        assertSame(old, middle.medium)
        assertSame(deep, parts[1])
        assertSame(far, parts[2])
        assertSame(remote, parts[3])
    }
}
