package krt.domain.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import krt.domain.camera.CameraView
import krt.domain.scene.World
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Material
import krt.domain.scene.model.Interface
import krt.domain.scene.model.Medium
import krt.domain.scene.model.Metal
import krt.domain.scene.model.VertexNormals
import krt.domain.scene.model.MediumInterface
import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Sky
import krt.domain.scene.model.Sphere
import krt.domain.scene.model.Triangle
import krt.platform.math.Color
import krt.platform.math.Point3

class GpuNumericalReferenceTest {
    private val z = Point3(0, 0, 1)

    @Test
    fun `a ray between a distant sphere and the corner of its bounding box misses`() {
        val ball = Sphere(z * 2048.0, 0.1, Emissive(Color.ONE, 1.0))
        val scene = World(listOf(ball), SceneLook.CLASSIC.copy(sky = Sky(Color.ZERO, Color.ZERO)))
        assertEquals(0, pixel(scene, Point3(0.09, 0.09, 0.0)))
        assertEquals(0xffffff, pixel(scene, Point3.ZERO))
    }

    @Test
    fun `rounding a BVH box cannot remove a ray inside the sphere`() {
        val grid = 1.0 / 2048
        val ball = Sphere(Point3(0.6 * grid, 0.0, 1.0), 64.6 * grid, Emissive(Color.ONE, 1.0))
        val scene = World(listOf(ball), SceneLook.CLASSIC.copy(sky = Sky(Color.ZERO, Color.ZERO)))
        // The true edge is 65.2 grid units. Quantized centre plus radius ends at 66.
        assertEquals(0xffffff, pixel(scene, Point3(65.1 * grid, 0.0, 0.0)))
    }

    @Test
    fun `a very bright sky channel saturates without losing the other two`() {
        val bright = Color(1e31, 0.25, 0.0)
        val scene = World(look = SceneLook.CLASSIC.copy(sky = Sky(bright, bright)))
        val rgb = pixel(scene, Point3.ZERO)
        assertEquals(255, rgb shr 16)
        assertTrue((rgb shr 8 and 255) in 127..128)
        assertEquals(0, rgb and 255)
    }

    @Test
    fun `a ray crossing into an unbounded clear medium still sees the sky`() {
        val boundary = Triangle(Point3(-1, -1, 1), Point3(0, 1, 1), Point3(1, -1, 1), Interface,
            media = MediumInterface(Medium(Color.ZERO, Color.ZERO)))
        val white = SceneLook.CLASSIC.copy(sky = Sky(Color.ONE, Color.ONE))
        assertEquals(0xffffff, pixel(World(listOf(boundary), white), Point3.ZERO))
    }

    @Test
    fun `long thin smooth triangles reflect along their interpolated normal`() {
        val width = 1.0 / 1024
        val normal = VertexNormals(z, Point3(0, 1, 0), z)
        val strip = Triangle(z, Point3(1024, 0, 1), Point3(1024.0, width, 1.0), Metal(Color.ONE, 0.0), normals = normal)
        val look = SceneLook.CLASSIC.copy(sky = Sky(Color(1, 0, 0), Color(0, 0, 1)))
        // u = v = 1/4, so n is (0,1,3)/sqrt(10). Reflection has y = -6/10,
        // hence the gradient sky supplies 80% red and 20% blue in linear light.
        val rgb = pixel(World(listOf(strip), look), Point3(512.0, width / 4, 0.0))
        assertTrue(rgb shr 16 in 227..229, "red ${rgb shr 16}")
        assertTrue((rgb and 255) in 113..115, "blue ${rgb and 255}")
    }

    @Test
    fun `an unchanged geometric ray still hits after its direction is rescaled`() {
        val white = Emissive(Color.ONE, 1.0)
        val world = World(listOf(panel(1.0, white)), SceneLook.CLASSIC.copy(sky = Sky(Color.ZERO, Color.ZERO)))
        assertEquals(0xffffff, pixel(world, Point3.ZERO, z * 1e-15))
    }

    @Test
    fun `a tiny but representable light is hit with a small direction`() {
        val side = 1.0 / 2048 // One GPU scene coordinate unit.
        val triangle = Triangle(Point3.ZERO, Point3(side, 0.0, 0.0), Point3(0.0, side, 0.0), Emissive(Color.ONE, 1.0))
        val scene = World(listOf(triangle), SceneLook.CLASSIC.copy(sky = Sky(Color.ZERO, Color.ZERO)))
        val origin = Point3(side / 4, side / 4, -1e-32)
        assertEquals(0xffffff, pixel(scene, origin, z * 1e-32))
    }

    @Test
    fun `coincident surfaces choose the scene's first material`() {
        val red = panel(1.0, Emissive(Color(1, 0, 0), 1.0))
        val blue = panel(1.0, Emissive(Color(0, 0, 1), 1.0))
        assertEquals(0xff0000, pixel(World(listOf(red, blue)), Point3.ZERO))
        assertEquals(0x0000ff, pixel(World(listOf(blue, red)), Point3.ZERO))
    }

    @Test
    fun `eight transparent shadow crossings transmit the same light as empty space`() {
        val look = SceneLook.CLASSIC.copy(sky = Sky(Color.ZERO, Color.ZERO), sampleLights = true)
        val receiver = panel(1.0, Lambert(Color.ONE))
        val lamp = panel(-10.0, Emissive(Color.ONE, 2.0))
        val clear = pixel(World(listOf(receiver, lamp), look), Point3.ZERO)
        assertTrue(clear > 0)
        val interfaces = (1..8).map { panel(-it.toDouble(), Interface) }
        assertEquals(clear, pixel(World(listOf(receiver, lamp) + interfaces, look), Point3.ZERO))
    }

    private fun panel(at: Double, material: Material) =
        Triangle(Point3(-20.0, -20.0, at), Point3(0.0, 20.0, at), Point3(20.0, -20.0, at), material)

    private fun pixel(world: World, origin: Point3, direction: Point3 = z): Int {
        val view = CameraView(origin, origin + direction, Point3.ZERO, Point3.ZERO, Point3.ZERO, Point3.ZERO, 0.0)
        val frame = GpuFrame(world.gpuScene, view, 2, 2, 4, 1, 0.5f)
        return rasterPixels(frame).first() and 0xffffff
    }
}
