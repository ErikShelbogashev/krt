package krt.domain.render

import java.awt.image.BufferedImage
import kotlin.math.abs

/** A second, independently sampled CPU image supplies the spatial pattern inside each 8×8 tile. */
internal class SceneDetailContrast(detail: BufferedImage, private val width: Int, height: Int) {
    private val scale = detail.width / width
    private val guide = Array(3) { channel ->
        DoubleArray(width * height) { i ->
            var sum = 0.0
            val x = (i % width) * scale
            val y = (i / width) * scale
            for (dy in 0 until scale) for (dx in 0 until scale) {
                sum += code(detail.getRGB(x + dx, y + dy), channel)
            }
            sum / (scale * scale)
        }
    }

    init {
        require(detail.width == width * scale && detail.height == height * scale && scale > 0)
    }

    fun differences(expected: IntArray, actual: IntArray): List<String> {
        val failures = mutableListOf<String>()
        val height = expected.size / width
        for (y in 0 until height step TILE) for (x in 0 until width step TILE) {
            val indexes = IntArray(TILE * TILE) { y * width + x + it / TILE * width + it % TILE }
            for (channel in guide.indices) {
                difference(indexes, channel, expected, actual)?.let { failures += "($x,$y), RGB $channel: $it" }
            }
        }
        return failures
    }

    private fun difference(indexes: IntArray, channel: Int, expected: IntArray, actual: IntArray): String? {
        val template = guide[channel]
        val mean = indexes.sumOf { template[it] } / indexes.size
        val weights = indexes.map { template[it] - mean }
        val weight = weights.sumOf { abs(it) }
        val errors = DoubleArray(indexes.size) { j ->
            val i = indexes[j]
            weights[j] * (code(actual[i], channel) - code(expected[i], channel))
        }
        val contrast = ContrastError.measure(errors, weight, floor = 3.0, deviations = 5.0) ?: return null
        return if (contrast.withinLimit) null else contrast.toString()
    }

    private companion object {
        const val TILE = 8
        fun code(rgb: Int, channel: Int) = rgb ushr (16 - 8 * channel) and 255
    }
}
