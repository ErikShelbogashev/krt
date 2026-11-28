package krt.domain.render

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import krt.domain.camera.CameraView
import krt.domain.scene.World
import krt.domain.scene.model.Interface
import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Sky
import krt.domain.scene.model.Triangle
import krt.domain.trace.PathTracing
import krt.platform.math.Point3
import krt.platform.math.Ray

class TransparentBoundaryTest {
    private val origin = Point3.ZERO
    private val direction = Point3(0, 0, 1)
    private val look = SceneLook.CLASSIC.copy(sky = Sky(Point3.ONE, Point3.ONE))

    @Test
    fun `camera rays reach the sky through the full boundary budget on CPU and SkSL`() {
        val world = interfaces(PathTracing.MAX_PATH_CROSSINGS)
        assertEquals(Point3.ONE, cpu(world))
        assertEquals(0xffffff, shader(world))
    }

    @Test
    fun `one extra boundary consistently terminates the path without sky light`() {
        val world = interfaces(PathTracing.MAX_PATH_CROSSINGS + 1)
        assertEquals(Point3.ZERO, cpu(world))
        assertEquals(0, shader(world))
    }

    @Test
    fun `a deep stack of transparent layers cannot overflow the render worker stack`() {
        assertEquals(Point3.ZERO, cpu(interfaces(10_000)))
    }

    private fun interfaces(count: Int) = World((1..count).map { z ->
        Triangle(Point3(-2, -2, z), Point3(2, -2, z), Point3(0, 2, z), Interface, "boundary-$z")
    }, look)

    private fun cpu(world: World) = RayTracer(world, 1).color(Ray(origin, direction), Random(1))

    private fun shader(world: World): Int {
        val view = CameraView(origin, direction, origin, origin, origin, origin, 0.0)
        return rasterPixels(GpuFrame(world.gpuScene, view, 2, 2, 1, 1, 0.5f)).first() and 0xffffff
    }
}
