package krt.platform.awt

import kotlin.math.sqrt
import krt.platform.math.Color

fun Color.toAWTColor(samplesPerPixel: Int): Int {
    fun Double.clamp(min: Double, max: Double): Double = when {
        this < min -> min
        this > max -> max
        else -> this
    }

    fun scaleTo255(x: Double) = (256 * sqrt(x).clamp(0.0, 0.990)).toInt()

    val scale = 1.0 / samplesPerPixel
    val r = scaleTo255(x * scale)
    val g = scaleTo255(y * scale)
    val b = scaleTo255(z * scale)
    return java.awt.Color(r, g, b).rgb
}
