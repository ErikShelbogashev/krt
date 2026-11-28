package krt.domain.render

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import krt.domain.camera.CameraView
import krt.domain.scene.World
import krt.domain.scene.model.Metal
import krt.domain.scene.model.Triangle
import krt.i9ns.pbrt.PbrtText
import krt.i9ns.pbrt.PbrtReader
import krt.platform.math.Point3
import krt.platform.math.Ray

class PbrtEmitterSideTest {
    private val source = checkNotNull(javaClass.getResource("/optics/area-light.pbrt")).readText()
    private val z = Point3(0, 0, 1)

    @Test
    fun `reversing an ordinary PBRT panel turns off its direct illumination of the receiver`() {
        // Rendering this reversed fixture in pbrt-v4 produces an entirely black image.
        val receiver = Ray(Point3(0, 0, 2), -z)
        assertLit(world(), receiver)
        assertDark(world(reverse = true), receiver)
        assertLit(world(reverse = true, both = true), receiver)
    }

    @Test
    fun `the same side of a lamp glows in direct and mirror views`() {
        val back = Ray(Point3(0.9, 0.0, 1.8), -z)
        assertDark(world(), back)
        assertLit(world(reverse = true), back)
        val mirror = Ray(Point3(-0.9, 0.0, 1.0), Point3(0.9, 0.0, -1.0))
        assertLit(world(mirror = true), mirror)
        assertDark(world(reverse = true, mirror = true), mirror)
    }

    @Test
    fun `a reversed spherical PBRT emitter glows toward its centre`() {
        val shape = "Shape \"sphere\" \"float radius\" 1"
        val lamp = "WorldBegin\nAreaLightSource \"diffuse\"\n"
        val outside = Ray(z * 2.0, -z)
        val inside = Ray(Point3.ZERO, z)
        fun sphere(prefix: String): World = PbrtReader.read(lamp + prefix + shape).let { World(it.objects, it.look) }
        assertLit(sphere(""), outside)
        assertDark(sphere(""), inside)
        assertDark(sphere("ReverseOrientation\n"), outside)
        assertLit(sphere("ReverseOrientation\n"), inside)
    }

    @Test
    fun `old krt files retain two sided light while ordinary included files follow PBRT`() {
        val receiver = Ray(Point3(0, 0, 2), -z)
        val reversed = source.replace("AreaLightSource", "ReverseOrientation\nAreaLightSource")
        fun scene(text: String, files: Map<String, ByteArray> = emptyMap()): World =
            PbrtReader.read(text, files).let { World(it.objects, it.look) }
        val header = PbrtText.KRT_HEADER + "\n"
        assertLit(scene(header + reversed), receiver)
        val files = mapOf("standard.pbrt" to reversed.toByteArray())
        assertDark(scene(header + "Include \"standard.pbrt\"", files), receiver)
    }

    private fun world(reverse: Boolean = false, both: Boolean = false, mirror: Boolean = false): World {
        val lamp = "AreaLightSource \"diffuse\""
        val orientation = if (reverse) "ReverseOrientation\n  " else ""
        val sides = if (both) " \"bool twosided\" true" else ""
        val scene = PbrtReader.read(source.replace(lamp, orientation + lamp + sides))
        val objects = scene.objects.map { obj ->
            if (mirror && obj is Triangle && !obj.material.emits) {
                Triangle(obj.v0, obj.v1, obj.v2, Metal(Point3.ONE, 0.0), obj.id)
            } else obj
        }
        return World(objects, scene.look)
    }

    private fun assertLit(world: World, ray: Ray) {
        val (cpu, shader) = signal(world, ray)
        assertTrue(cpu > 0.001 && shader > 0, "emitter expected: CPU $cpu, shader code $shader")
    }

    private fun assertDark(world: World, ray: Ray) {
        val (cpu, shader) = signal(world, ray)
        assertEquals(0.0, cpu)
        assertEquals(0, shader)
    }

    private fun signal(world: World, ray: Ray): Pair<Double, Int> {
        val cpu = RayTracer(world, 2)
        val rng = Random(19)
        val mean = (0 until 32).sumOf { cpu.color(ray, rng).x } / 32
        val view = CameraView(ray.origin, ray.origin + ray.direction, Point3.ZERO, Point3.ZERO,
            Point3.ZERO, Point3.ZERO, 0.0)
        val frame = GpuFrame(world.gpuScene, view, 2, 2, 2, 16, 0.31f)
        return mean to (rasterPixels(frame).first() and 255)
    }
}
