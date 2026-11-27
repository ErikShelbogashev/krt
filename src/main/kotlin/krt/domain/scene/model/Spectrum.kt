package krt.domain.scene.model

import kotlin.math.exp
import krt.platform.math.Color

/**
 * Visible light by wavelength, for materials that split white light (ADR 0013). A wavelength is drawn uniformly
 * from [MIN_NM] to [MAX_NM]; its [weight] is the colour it carries, scaled so that the weights of all wavelengths
 * average to white: a path that picked one wavelength stays an unbiased sample of white light.
 */
object Spectrum {
    const val MIN_NM = 380.0
    const val MAX_NM = 720.0

    /** Wavelengths at which a material that changes with wavelength is sampled for the red, green and blue channels. */
    const val RED_NM = 650.0
    const val GREEN_NM = 532.0
    const val BLUE_NM = 450.0

    /** The wavelength for a uniform number [u] in [0, 1). */
    fun sample(u: Double): Double = MIN_NM + (MAX_NM - MIN_NM) * u

    /** Colour of light of [wavelength] nm: Gaussian bumps per channel, red with a violet tail, each of mean 1. */
    fun weight(wavelength: Double): Color = Color(
        RED_SCALE * (bump(wavelength, RED_PEAK, RED_WIDTH) + VIOLET * bump(wavelength, VIOLET_PEAK, VIOLET_WIDTH)),
        GREEN_SCALE * bump(wavelength, GREEN_PEAK, GREEN_WIDTH),
        BLUE_SCALE * bump(wavelength, BLUE_PEAK, BLUE_WIDTH),
    )

    private fun bump(x: Double, peak: Double, width: Double): Double = exp(-((x - peak) / width).let { it * it })

    private const val RED_PEAK = 610.0
    private const val RED_WIDTH = 50.0
    private const val VIOLET_PEAK = 425.0
    private const val VIOLET_WIDTH = 25.0
    private const val VIOLET = 0.15
    private const val GREEN_PEAK = 545.0
    private const val GREEN_WIDTH = 45.0
    private const val BLUE_PEAK = 450.0
    private const val BLUE_WIDTH = 35.0

    /** One over the mean of each channel's bumps over the visible range; the shader has the same numbers. */
    private const val RED_SCALE = 3.5733
    private const val GREEN_SCALE = 4.2628
    private const val BLUE_SCALE = 5.4935
}
