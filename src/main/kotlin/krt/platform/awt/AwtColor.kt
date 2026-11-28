package krt.platform.awt

import kotlin.math.sqrt
import krt.platform.math.Color

private const val MAX_CHANNEL = 255.0
private const val HALF_STEP = 0.5

fun Color.toAWTColor(samplesPerPixel: Int): Int {
    fun scaleTo255(x: Double) = (MAX_CHANNEL * sqrt(x.coerceIn(0.0, 1.0)) + HALF_STEP).toInt()

    val scale = 1.0 / samplesPerPixel
    val r = scaleTo255(x * scale)
    val g = scaleTo255(y * scale)
    val b = scaleTo255(z * scale)
    return java.awt.Color(r, g, b).rgb
}
