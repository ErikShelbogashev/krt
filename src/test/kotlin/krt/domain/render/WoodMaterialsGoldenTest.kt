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
import krt.domain.scene.model.DemoScene
import krt.domain.scene.model.Patterned
import krt.domain.scene.model.WoodGrain
import krt.i9ns.scenefile.SceneLibrary
import krt.platform.math.Ray

class WoodMaterialsGoldenTest {
    private val scene = SceneLibrary().load(DemoScene.MATERIALS)
    private val world = World(scene.objects, scene.look)
    private val camera = Camera.default().apply {
        imageWidth = 256
        imageHeight = 160
        resetView(scene.look)
        filterWood = true
    }
    private val view = camera.view()
    // Hold one aperture point fixed to isolate the surface footprint from defocus.
    private val lens = camera.ray(0.5, 0.5, Random(19)).origin
    private val file = File("src/test/resources/golden/wood/materials-floor.png")

    @Test
    fun `the imported floor matches its supersampled surface from foreground to horizon`() {
        val reference = ImageIO.read(file).raster
        for (row in 0 until 2 * ROWS) for (column in 0 until WIDTH) {
            val x = left(row) + column
            val y = top(row) + row % ROWS
            val value = supersample(x, y)
            val delta = reference.getSample(column, row, 0) - (value * 65535).roundToInt()
            assertTrue(abs(delta) <= 1, "Imported floor ($x,$y) differs by $delta")
        }
    }

    @Test
    fun `projecting the pixel on the Materials floor reduces albedo error in both patches`() {
        val reference = ImageIO.read(file).raster
        for (band in 0..1) {
            var pointError = 0.0
            var filterError = 0.0
            for (row in 0 until ROWS) for (column in 0 until WIDTH) {
                val imageRow = band * ROWS + row
                val x = left(imageRow) + column + 0.5
                val y = top(imageRow) + row + 0.5
                val expected = reference.getSample(column, imageRow, 0) / 65535.0
                val ray = camera.ray(x / 256, 1 - y / 160, Random(19))
                val hit = requireNotNull(world.traceScene.hit(ray, 0.001, Double.POSITIVE_INFINITY))
                val wood = (hit.material as Patterned).pattern
                val pixel = requireNotNull(ray.pixel).onSurface(ray.direction, hit.normal, hit.t)
                val raw = wood.valueAt(hit.p)
                val filtered = wood.valueAt(hit.p, WoodGrain.filter(wood, hit.p, pixel))
                pointError += abs(raw - expected)
                filterError += abs(filtered - expected)
            }
            val ratio = if (band == 0) 0.65 else 1.0
            assertTrue(filterError < pointError * ratio, "patch $band: filtered $filterError vs point $pointError")
        }
    }

    private fun supersample(x: Int, y: Int): Double {
        var sum = 0.0
        for (j in 0 until GRID) for (i in 0 until GRID) {
            val s = (x + (i + 0.5) / GRID) / 256
            val t = 1 - (y + (j + 0.5) / GRID) / 160
            val ray = Ray(lens, view.lowerLeftCorner + view.horizontal * s + view.vertical * t - lens)
            val hit = requireNotNull(world.traceScene.hit(ray, 0.001, Double.POSITIVE_INFINITY))
            sum += (hit.material as Patterned).pattern.valueAt(hit.p)
        }
        return sum / (GRID * GRID)
    }

    private fun top(row: Int) = if (row < ROWS) 65 else 124
    private fun left(row: Int) = if (row < ROWS) 3 else 16

    private companion object {
        const val WIDTH = 24
        const val ROWS = 8
        const val GRID = 32
    }
}
