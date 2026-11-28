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
        for (cavityId in listOf("cavity", outer.id)) {
            val cavity = Sphere(Point3.ZERO, 1.0, Interface, cavityId, media = MediumInterface(null, old))
            val sky = Color.ONE * 0.25
            val world = World(listOf(outer, cavity), SceneLook.CLASSIC.copy(sky = Sky(sky, sky)))
            world.editObject(outer.id, edit)
            assertShellTransmission(world)
        }
    }

    private fun assertShellTransmission(world: World) {
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
    fun `mesh shells with a shared id and one shared vertex restore the medium on every inner face`() {
        fun shell(radius: Int, media: MediumInterface) =
            Meshes.box(Pose(Point3.ZERO), Point3(radius, radius, radius), Interface, media).map {
                Triangle(it.v0, it.v1, it.v2, it.material, "shell", media = media)
            }
        val outer = shell(3, MediumInterface(old))
        fun stretch(vertex: Point3) = if (vertex == Point3.ONE) Point3(3, 3, 3) else vertex
        val cavity = shell(1, MediumInterface(null, old)).map {
            Triangle(stretch(it.v0), stretch(it.v1), stretch(it.v2), it.material, it.id, media = it.media)
        }
        val world = World(outer + cavity)
        world.editObject("shell", edit)
        val next = old.copy(density = Color(0.0, 0.25, 0.0))
        val faces = world.objects.filterIsInstance<Triangle>()
        assertEquals(setOf(MediumInterface(next)), faces.take(outer.size).map { it.media }.toSet())
        assertEquals(setOf(MediumInterface(null, next)), faces.drop(outer.size).map { it.media }.toSet())
    }

    @Test
    fun `aligned cavities in a fog bank all restore their enclosing medium`() {
        val direction = Point3(1.125, kotlin.math.sqrt(2.0) / 8.0, kotlin.math.sqrt(3.0) / 8.0)
        val count = 4096
        val bank = Fog(Point3.ZERO, 2.0 * count, old, "bank")
        val cavities = List(count) { index ->
            Sphere(direction * index.toDouble(), 0.1, Interface, "cavity-$index",
                media = MediumInterface(null, old))
        }
        val world = World(listOf(bank) + cavities)
        world.editObject(bank.id, edit)
        val next = old.copy(density = Color(0.0, 0.25, 0.0))
        assertEquals(setOf(next), world.objects.filterIsInstance<Sphere>().map { it.media?.outside }.toSet())
    }

    @Test
    fun `moving a cavity between two equal media changes which edits reach its outside`() {
        val next = old.copy(density = Color(0.0, 0.25, 0.0))
        val offset = Point3(12, 0, 0)
        val left = Fog(Point3.ZERO, 3.0, old, "left")
        val right = Fog(offset, 3.0, next, "right")
        val cavity = Sphere(Point3.ZERO, 1.0, Interface, "cavity", media = MediumInterface(null, old))
        val world = World(listOf(left, right, cavity))
        world.editObject("left", edit)
        world.translateObjectById("cavity", offset)
        val stronger = ObjectChange.Extinction(ObjectChange.Channel.G, 0.5)
        world.editObject("left", stronger)
        assertEquals(next, (world.objects.last() as Sphere).media?.outside)
        world.editObject("right", stronger)
        assertEquals(0.5, (world.objects.last() as Sphere).media?.outside?.density?.y)
        world.editObject("right", edit)
        assertEquals(next, (world.objects.last() as Sphere).media?.outside)
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
