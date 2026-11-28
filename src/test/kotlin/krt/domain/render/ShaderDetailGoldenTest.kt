package krt.domain.render

import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.assertTrue
import krt.domain.scene.model.DemoScene
import krt.i9ns.scenefile.SceneLibrary
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

class ShaderDetailGoldenTest {
    @TestFactory
    fun features() = DemoScene.entries.flatMap { scene ->
        val view by lazy { FeatureView(scene) }
        listOf(0.31f, 0.73f).map { seed ->
            dynamicTest("${scene.title} full-resolution shader detail, seed $seed") {
                val pixels = view.render(seed)
                val errors = view.differences(pixels)
                assertTrue(errors.all { it.withinLimit }, "${view.feature.name}: $errors")
                if (scene == DemoScene.CANDLE) {
                    val blurred = view.differences(view.blur(pixels))
                    assertTrue(blurred.any { !it.withinLimit }, "3×3 blur of the flame edge passed: $blurred")
                }
            }
        }
    }
}

/** Independent CPU and shader samples of the feature at the original film coordinates. */
private class FeatureView(demo: DemoScene) {
    val feature = DetailFeature.forScene(demo)
    private val view = DetailView(SceneLibrary().load(demo))
    private val independent = IntArray(feature.width * feature.height) { i ->
        val x = feature.left + i % feature.width
        val y = feature.top + i / feature.width
        view.samplePixel(x, y, seed = 997L, samples = SAMPLES)
    }
    private val file = File("src/test/resources/golden/detail", demo.path.substringBeforeLast('.') + ".png")
    private val golden = ImageIO.read(file).getRGB(feature.left, feature.top, feature.width, feature.height, null, 0,
        feature.width)

    fun render(seed: Float): IntArray =
        rasterPixels(view.shaderFrame(seed, SAMPLES), feature.left, feature.top, feature.width, feature.height)

    fun differences(pixels: IntArray): List<ContrastError> {
        val smooth = blur(golden)
        val means = SHIFTS.associateWith { shift -> golden.map { code(it, shift) }.average() }
        return listOf(
            comparison(pixels) { i, c -> code(golden[i], c) - means.getValue(c) },
            comparison(pixels) { i, c -> (code(golden[i], c) - code(smooth[i], c)).toDouble() },
        )
    }

    private fun comparison(pixels: IntArray, weight: (Int, Int) -> Double): ContrastError {
        var totalWeight = 0.0
        val errors = DoubleArray(pixels.size) { i ->
            SHIFTS.sumOf { shift ->
                val w = weight(i, shift)
                totalWeight += abs(w)
                w * (code(pixels[i], shift) - code(independent[i], shift))
            }
        }
        return requireNotNull(ContrastError.measure(errors, totalWeight, floor = 1.0, deviations = 4.0)) {
            "Golden feature has no spatial contrast: ${feature.name}"
        }
    }

    fun blur(pixels: IntArray): IntArray = IntArray(pixels.size) { i ->
        val x = i % feature.width
        val y = i / feature.width
        SHIFTS.sumOf { shift -> blurredChannel(pixels, x, y, shift) shl shift }
    }

    private fun blurredChannel(pixels: IntArray, x: Int, y: Int, shift: Int): Int {
        var sum = 0
        for (dy in -1..1) for (dx in -1..1) {
            val row = (y + dy).coerceIn(0, feature.height - 1)
            val column = (x + dx).coerceIn(0, feature.width - 1)
            sum += code(pixels[row * feature.width + column], shift)
        }
        return (sum + 4) / 9
    }

    private fun code(pixel: Int, shift: Int) = pixel ushr shift and 255

    private companion object {
        const val SAMPLES = 32
        val SHIFTS = listOf(16, 8, 0)
    }
}
