package krt.domain.render

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import krt.domain.camera.CameraView
import krt.domain.scene.World
import krt.domain.scene.model.EmissionSide
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Fog
import krt.domain.scene.model.Medium
import krt.domain.scene.model.ObjectChange
import krt.domain.scene.model.ObjectOptics
import krt.domain.scene.model.Sphere
import krt.domain.scene.model.Triangle
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

class SceneOpticsEditTest {
    @Test
    fun `editing an emitter updates every mesh part and both active renderers`() {
        val lamp = Emissive(Color(0.25, 0, 0), 1.0, EmissionSide.FRONT)
        val source = Triangle(Point3(-2, -2, 0), Point3(2, -2, 0), Point3(0, 2, 0), lamp, "lamp")
        val other = Sphere(Point3(10, 10, 10), 1.0, lamp, "other")
        val world = World(listOf(source, source.translate(Point3(5, 0, 0)), other))
        val tracer = RayTracer(world, 2)
        val eye = Point3(0, 0, 1)
        val view = CameraView(eye, Point3.ZERO, Point3.ZERO, Point3.ZERO, Point3.ZERO, Point3.ZERO, 0.0)
        for ((scale, red) in listOf(1.0 to 128, 0.0 to 0, 4.0 to 255)) {
            world.editObject("lamp", ObjectChange.Emission(scale))
            assertSame(other, world.objects.last())
            assertEquals(listOf(scale, scale), world.objects.take(2).map(ObjectOptics::emissionScale))
            assertEquals(EmissionSide.FRONT, ((world.objects.first() as Triangle).material as Emissive).emissionSide)
            assertEquals(Color(0.25 * scale, 0, 0), tracer.color(Ray(eye, -eye), Random(1)))
            val pixels = rasterPixels(GpuFrame(world.gpuScene, view, 4, 4, 2, 1, 0.1f))
            for (pixel in pixels) assertEquals(red, pixel shr 16 and 255)
        }
    }

    @Test
    fun `an OBJ group with a matte first face still exposes its light`() {
        val matte = Sphere(Point3.ZERO, 1.0, Lambert(Color.ONE), "mesh")
        val light = Sphere(Point3.ONE, 1.0, Emissive(Color.ONE, 10.0), "mesh")
        val world = World(listOf(matte, light))
        assertEquals(10.0, ObjectOptics.selected(world.objects, "mesh").emission)
        world.editObject("mesh", ObjectChange.Emission(0.5))
        assertSame(matte, world.objects.first())
        assertEquals(0.5, ObjectOptics.selected(world.objects, "mesh").emission)
    }

    @Test
    fun `extinction changes one channel and retains scattering properties and outside medium`() {
        val inside = Medium(Color(0.07, 0.06, 0.05), Color(0.7, 0.8, 0.9), 0.75)
        val outside = Medium.grey(0.01, Color.ONE)
        val fog = Fog(Point3.ZERO, 5.0, inside, "haze", outside)
        val change = ObjectChange.Extinction(ObjectChange.Channel.G, 0.0)
        val edited = change.applyTo(fog) as Fog
        assertEquals(inside.copy(density = Color(0.07, 0.0, 0.05)), edited.medium)
        assertSame(outside, edited.outside)
        assertEquals(1.0, edited.medium.transmittance(8.0).y)
        assertFailsWith<IllegalArgumentException> { change.copy(value = Double.NaN).applyTo(fog) }
    }
}
