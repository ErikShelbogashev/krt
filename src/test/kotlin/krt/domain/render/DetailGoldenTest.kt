package krt.domain.render

import java.awt.image.BufferedImage
import java.io.File
import java.util.stream.IntStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import krt.domain.camera.Camera
import krt.domain.scene.World
import krt.domain.scene.model.DemoScene
import krt.domain.scene.model.SceneDescription
import krt.i9ns.scenefile.SceneLibrary
import krt.platform.awt.toAWTColor
import krt.platform.math.Point3
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

/** Higher resolution than the end-to-end CPU/SkSL checks so thin features cover several pixels. */
class DetailGoldenTest {
    @TestFactory
    fun scenes() = DemoScene.entries.flatMap { scene ->
        val view by lazy { DetailView(SceneLibrary().load(scene)) }
        val reference by lazy {
            val name = scene.path.substringBeforeLast('.')
            val image = ImageIO.read(File("src/test/resources/golden/detail/$name.png"))
            assertEquals(DetailView.WIDTH, image.width)
            assertEquals(DetailView.HEIGHT, image.height)
            image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        }
        DetailView.bands.map { top ->
            dynamicTest("${scene.title} rows $top through ${top + DetailView.BAND - 1}") {
                val actual = view.band(top)
                val violations = DetailDifference.compare(reference, actual, top) +
                    DetailFeature.forScene(scene).compare(reference, actual, top)
                if (violations.isNotEmpty()) {
                    val name = scene.path.substringBeforeLast('.')
                    val file = File("build/reports/golden/detail/$name-$top.png")
                    file.parentFile.mkdirs()
                    val image = BufferedImage(DetailView.WIDTH, DetailView.BAND, BufferedImage.TYPE_INT_RGB)
                    image.setRGB(0, 0, image.width, image.height, actual, 0, image.width)
                    ImageIO.write(image, "png", file)
                }
                assertTrue(violations.isEmpty(), violations.joinToString("\n"))
            }
        }
    }
}

/** Rays, jitter, per-pass seeds, accumulation order and conversion to RGB follow the CPU render path. */
internal class DetailView(scene: SceneDescription) {
    private val camera = Camera.default().apply {
        imageWidth = WIDTH
        imageHeight = HEIGHT
        resetView(scene.look)
    }
    private val tracer = RayTracer(World(scene.objects, scene.look), maxDepth = 7)

    fun band(top: Int): IntArray {
        val rgb = IntArray(WIDTH * BAND)
        IntStream.range(0, BAND).parallel().forEach { row ->
            val y = HEIGHT - 1 - (top + row)
            for (x in 0 until WIDTH) {
                val pixel = FrameSampling.Pixel(x, y)
                var sum = Point3.ZERO
                for (pass in 0 until SAMPLES) {
                    val random = FrameSampling.passRandom(42L, pixel, WIDTH, HEIGHT, pass)
                    sum += FrameSampling.sampleOnce(pixel, camera, tracer, random)
                }
                rgb[row * WIDTH + x] = sum.toAWTColor(SAMPLES)
            }
        }
        return rgb
    }

    companion object {
        const val WIDTH = 256
        const val HEIGHT = 160
        const val BAND = 20
        const val SAMPLES = 48
        val bands = 0 until HEIGHT step BAND
    }
}

/** RGB code values (0..255). Compare absolute changes so bright and dark errors cannot cancel. */
internal object DetailDifference {
    private const val WIDTH = DetailView.WIDTH
    private const val TILE_WIDTH = 8
    private const val TILE_HEIGHT = 10
    private const val MAX_TILE_MAE = 2.5
    private const val MAX_EDGE_MAE = 4.0
    private const val MAX_BAND_MAE = 0.16
    private const val OUTLIER = 16
    private const val MAX_BAND_OUTLIERS = 8

    fun compare(reference: IntArray, actual: IntArray, top: Int): List<String> {
        val errors = Array(3) { ch ->
            IntArray(actual.size) { i -> channel(actual[i], ch) - channel(reference[top * WIDTH + i], ch) }
        }
        val findings = mutableListOf<String>()
        val mean = errors.sumOf { channel -> channel.sumOf { abs(it) } } / (3.0 * actual.size)
        val outliers = actual.indices.count { i -> errors.any { abs(it[i]) > OUTLIER } }
        if (mean > MAX_BAND_MAE || outliers > MAX_BAND_OUTLIERS) {
            findings += "band at $top: RGB MAE $mean, $outliers pixels differing by >$OUTLIER"
        }
        for (y in 0 until DetailView.BAND step TILE_HEIGHT) for (x in 0 until WIDTH step TILE_WIDTH) {
            for ((ch, channel) in errors.withIndex()) {
                val difference = tileDifference(channel, x, y)
                if (difference.mae > MAX_TILE_MAE || difference.edgeMae > MAX_EDGE_MAE) {
                    findings += "tile ($x,${top + y}), channel ${"BGR"[ch]}: $difference"
                }
            }
        }
        return findings
    }

    private data class TileDifference(val mae: Double, val edgeMae: Double)

    /** A difference of neighbouring signed errors is the change in their signed contrast. */
    private fun tileDifference(errors: IntArray, left: Int, top: Int): TileDifference {
        var sum = 0
        var edges = 0
        var edgeCount = 0
        for (y in top until top + TILE_HEIGHT) for (x in left until left + TILE_WIDTH) {
            val i = y * WIDTH + x
            sum += abs(errors[i])
            if (x > 0) {
                edges += abs(errors[i] - errors[i - 1])
                edgeCount++
            }
            if (y > 0) {
                edges += abs(errors[i] - errors[i - WIDTH])
                edgeCount++
            }
        }
        return TileDifference(sum.toDouble() / (TILE_WIDTH * TILE_HEIGHT), edges.toDouble() / edgeCount)
    }

    private fun channel(rgb: Int, ch: Int) = (rgb ushr (8 * ch)) and 255
}


/** An edge or pattern visible in the shipped start view, wholly contained within one test band. */
internal data class DetailFeature(val name: String, val left: Int, val top: Int, val width: Int, val height: Int) {
    fun compare(reference: IntArray, actual: IntArray, bandTop: Int): List<String> {
        if (top !in bandTop until bandTop + DetailView.BAND) return emptyList()
        val horizontal = edges(reference, actual, bandTop, dx = 1, dy = 0)
        val vertical = edges(reference, actual, bandTop, dx = 0, dy = 1)
        val channels = 3.0 * ((width - 1) * height + width * (height - 1))
        val referenceContrast = (horizontal.contrast + vertical.contrast) / channels
        val edgeMae = (horizontal.error + vertical.error) / channels
        val tolerance = maxOf(0.35, referenceContrast * 0.06)
        return if (edgeMae <= tolerance) emptyList() else listOf(
            "$name at ($left,$top): edge error $edgeMae exceeds $tolerance (contrast $referenceContrast)",
        )
    }

    private data class Edges(val contrast: Int, val error: Int)

    private fun edges(reference: IntArray, actual: IntArray, bandTop: Int, dx: Int, dy: Int): Edges {
        val offset = dy * DetailView.WIDTH + dx
        var contrast = 0
        var error = 0
        for (y in top + dy until top + height) for (x in left + dx until left + width) {
            val r = y * DetailView.WIDTH + x
            val i = r - bandTop * DetailView.WIDTH
            for (shift in SHIFTS) {
                val expected = (reference[r] ushr shift and 255) - (reference[r - offset] ushr shift and 255)
                val observed = (actual[i] ushr shift and 255) - (actual[i - offset] ushr shift and 255)
                contrast += abs(expected)
                error += abs(observed - expected)
            }
        }
        return Edges(contrast, error)
    }

    companion object {
        private val SHIFTS = intArrayOf(0, 8, 16)

        fun forScene(scene: DemoScene): DetailFeature = when (scene) {
            DemoScene.SHOWCASE -> DetailFeature("torus knot", 106, 40, 36, 18)
            DemoScene.MATERIALS -> DetailFeature("wood grain", 16, 124, 42, 14)
            DemoScene.FRACTALS -> DetailFeature("fractal silhouette", 108, 42, 32, 17)
            DemoScene.PRISM -> DetailFeature("spectral slit", 124, 62, 25, 16)
            DemoScene.BUBBLES -> DetailFeature("bubble rim", 118, 40, 34, 20)
            DemoScene.CANDLE -> DetailFeature("flame outline", 75, 22, 20, 17)
            DemoScene.TEMPLE -> DetailFeature("tunnel opening", 117, 65, 24, 14)
            DemoScene.METALS -> DetailFeature("softbox reflection", 43, 66, 28, 12)
            DemoScene.CLASSIC -> DetailFeature("small sphere silhouettes", 60, 85, 54, 14)
        }
    }
}
