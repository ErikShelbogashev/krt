package krt.domain.render

import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import krt.domain.camera.Camera
import krt.domain.scene.World
import krt.domain.scene.model.Fog
import krt.domain.scene.model.Pattern
import krt.domain.scene.model.PatternKind
import krt.domain.scene.model.Patterned
import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Sky
import krt.domain.scene.model.Triangle
import krt.domain.scene.model.VertexNormals
import krt.platform.math.Point3

/** White sky reflects equally in red and green; only the diffuse wood contributes a red-only sample. */
class WoodFootprintGoldenTest {
    private val origin = Point3(0.0, 0.15, 0.0)
    private val up = Point3(0, 1, 0)
    private val camera = Camera(origin, origin + Point3(0, 0, 1), up, 60.0, 0.0, 1.0, 256, 160)
    private val wood = Pattern(PatternKind.WOOD, Point3.ZERO, Point3(1, 0, 0), 2.0, 0.35)
    private val material = Patterned(wood, roughness = 0.0)
    private val floor = Triangle(Point3(-100, 0, -30), Point3(0, 0, 100), Point3(100, 0, -30), material)
    private val world = World(listOf(floor), SceneLook(Sky(Point3.ONE, Point3.ONE), 60.0, 0.0))
    private val file = File("src/test/resources/golden/wood/plane.png")

    @Test
    fun `the two 16 bit patches are stratified box integrals of the unfiltered wood`() {
        val reference = ImageIO.read(file).raster
        for (row in 0 until 2 * ROWS) for (x in 0 until WIDTH) {
            val value = supersample(LEFT + x, top(row) + row % ROWS)
            val delta = reference.getSample(x, row, 0) - (value * 65535).roundToInt()
            assertTrue(abs(delta) <= 1, "golden pixel ($x,$row) differs by $delta")
        }
    }

    @Test
    fun `CPU resolves nearby wood and suppresses distant aliases at one sample per pixel`() = compare(shader = false)

    @Test
    fun `SkSL resolves nearby wood and suppresses distant aliases at one sample per pixel`() = compare(shader = true)

    @Test
    fun `CPU suppresses false intensity changes as the camera translates along the floor`() = motion(shader = false)

    @Test
    fun `SkSL suppresses false intensity changes as the camera translates along the floor`() = motion(shader = true)

    private fun motion(shader: Boolean) {
        val top = top(0)
        val reference = ImageIO.read(file).raster
        val raw = render(shader, top, filtered = false)
        val filtered = render(shader, top, filtered = true)
        val moved = origin + Point3(0.0, 0.0, 0.045)
        camera.lookFrom(moved, moved + Point3(0, 0, 1))
        val nextRaw = render(shader, top, filtered = false)
        val nextFiltered = render(shader, top, filtered = true)
        val diffuse = raw.indices.filter { i ->
            raw[i] >= 0 && nextRaw[i] >= 0 && filtered[i] >= 0 && nextFiltered[i] >= 0
        }
        val expected = diffuse.map { i ->
            val x = i % WIDTH
            val y = i / WIDTH
            supersample(LEFT + x, top + y) - reference.getSample(x, y, 0) / 65535.0
        }
        fun motionError(before: DoubleArray, after: DoubleArray) = diffuse.mapIndexed { at, i ->
            abs(after[i] - before[i] - expected[at])
        }.average()
        val pointError = motionError(raw, nextRaw)
        val filterError = motionError(filtered, nextFiltered)
        assertTrue(diffuse.size > 45)
        assertTrue(expected.any { abs(it) > 0.02 }, "The reference contains no visible motion")
        assertTrue(filterError < pointError * 0.75, "temporal error $filterError, point sampled $pointError")
    }

    @Test
    fun `CPU keeps the wood footprint across clear boundaries and smooth shading`() = geometric(shader = false)

    @Test
    fun `SkSL keeps the wood footprint across clear boundaries and smooth shading`() = geometric(shader = true)

    private fun geometric(shader: Boolean) {
        val tilt = Point3(0.0, 1.0, -0.32).unit()
        val smooth = Triangle(floor.v0, floor.v1, floor.v2, material, normals = VertexNormals(tilt, tilt, tilt))
        val fog = Fog(Point3(0, 0.15, 1.65), 1.1, 0.0, Point3.ONE)
        val expected = render(shader, top(0), true)
        for (objects in listOf(listOf(floor, fog), listOf(smooth))) {
            val actual = render(shader, top(0), true, World(objects, world.look))
            val diffuse = expected.indices.filter { expected[it] >= 0 && actual[it] >= 0 }
            assertTrue(diffuse.size > 30)
            val mae = diffuse.map { abs(actual[it] - expected[it]) }.average()
            assertTrue(mae < 0.001, "A geometric footprint changed by $mae, shader=$shader")
        }
    }

    private fun compare(shader: Boolean) {
        val reference = ImageIO.read(file).raster
        for (band in 0..1) {
            val top = top(band * ROWS)
            val raw = render(shader, top, filtered = false)
            val filtered = render(shader, top, filtered = true)
            val diffuse = raw.indices.filter { raw[it] >= 0 && filtered[it] >= 0 }
            val expected = diffuse.map { i -> reference.getSample(i % WIDTH, band * ROWS + i / WIDTH, 0) / 65535.0 }
            val average = expected.average()
            val flatError = expected.map { abs(it - average) }.average()
            fun error(samples: DoubleArray) = diffuse.mapIndexed { at, i -> abs(samples[i] - expected[at]) }.average()
            val pointError = error(raw)
            val filterError = error(filtered)
            val details = "row $top, point $pointError, filtered $filterError, flat $flatError"
            assertTrue(diffuse.size > 45, "Only ${diffuse.size} samples reached the diffuse floor")
            if (band == 0) {
                assertTrue(filterError < pointError * 0.75 && filterError < flatError, details)
            } else {
                assertTrue(filterError < flatError * 0.5 && filterError <= pointError + 0.005, details)
            }
        }
    }

    private fun render(shader: Boolean, top: Int, filtered: Boolean, scene: World = world): DoubleArray {
        camera.filterWood = filtered
        if (shader) {
            val frame = GpuFrame(scene.gpuScene, camera.view(), 256, 160, 2, 1, 0.31f)
            return rasterPixels(frame, LEFT, top, WIDTH, ROWS).map { pixel ->
                val red = (pixel ushr 16 and 255) / 255.0
                if ((pixel ushr 8 and 255) == 0) red * red else -1.0
            }.toDoubleArray()
        }
        val tracer = RayTracer(scene, 2)
        return DoubleArray(WIDTH * ROWS) { i ->
            val pixel = FrameSampling.Pixel(LEFT + i % WIDTH, 159 - top - i / WIDTH)
            val color = FrameSampling.sampleOnce(pixel, camera, tracer, Random(i + 57))
            if (color.y == 0.0) color.x else -1.0
        }
    }

    /** Intersect every individual subpixel with the plane; this oracle uses neither ray derivatives nor the filter. */
    private fun supersample(x: Int, y: Int): Double {
        val view = camera.view()
        val eye = camera.origin
        var sum = 0.0
        for (j in 0 until GRID) for (i in 0 until GRID) {
            val s = (x + (i + 0.5) / GRID) / 256
            val t = 1 - (y + (j + 0.5) / GRID) / 160
            val direction = view.lowerLeftCorner + view.horizontal * s + view.vertical * t - eye
            sum += wood.valueAt(eye - direction * (eye.y / direction.y))
        }
        return sum / (GRID * GRID)
    }

    private fun top(row: Int) = if (row < ROWS) 82 else 150

    private companion object {
        const val LEFT = 63
        const val WIDTH = 128
        const val ROWS = 8
        const val GRID = 32
    }
}
