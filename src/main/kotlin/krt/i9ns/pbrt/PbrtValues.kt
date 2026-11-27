package krt.i9ns.pbrt

import kotlin.math.pow
import kotlin.math.sqrt
import krt.domain.scene.model.Noise
import krt.domain.scene.model.Pattern
import krt.domain.scene.model.PatternKind
import krt.platform.math.Color
import krt.platform.math.Point3

/** Values of pbrt-v4 parameters as krt takes them (ADR 0016): pure conversions. */
object PbrtValues {
    const val DEFAULT_ETA = 1.5
    private const val D_LINE_UM = 0.5876
    private const val NM_PER_UM = 1000.0
    private const val QUARTER = 0.25
    private const val RGB = 3

    /** Named glasses of pbrt-v4 as Cauchy fits: refraction index at the d line and dispersion in µm². */
    @Suppress("MagicNumber") // measured data
    private val GLASSES = mapOf(
        "glass-BK7" to (1.5168 to 0.0042),
        "glass-BAF10" to (1.6700 to 0.0086),
        "glass-FK51A" to (1.4866 to 0.0027),
        "glass-LASF9" to (1.8503 to 0.0145),
        "glass-F5" to (1.6034 to 0.0090),
        "glass-F10" to (1.6200 to 0.0095),
        "glass-F11" to (1.6210 to 0.0096),
        "glass-SF5" to (1.6727 to 0.0110),
        "glass-SF10" to (1.7283 to 0.0134),
        "glass-SF11" to (1.7847 to 0.0155),
    )

    /** A colour parameter: three numbers, one for grey, or [default]. */
    fun color(d: PbrtDirective, name: String, default: Color): Color {
        val values = d.params[name]?.values?.filterIsInstance<Double>() ?: return default
        return when {
            values.size >= RGB -> Color(values[0], values[1], values[2])
            values.size == 1 -> Color(values[0], values[0], values[0])
            else -> default
        }
    }

    /** A vector parameter, or null. */
    fun vector(d: PbrtDirective, name: String): Point3? = d.params[name]?.doubles()?.let { Point3(it[0], it[1], it[2]) }

    /** A krt pattern from the parameters of a `#krt` directive. */
    fun pattern(d: PbrtDirective): Pattern = Pattern(
        PatternKind.valueOf(d.text("kind", "noise").uppercase()),
        color(d, "first", Point3.ZERO),
        color(d, "second", Point3.ONE),
        d.double("scale", 1.0),
        d.double("turbulence", Pattern.DEFAULT_TURBULENCE),
        d.double("frequency", 1.0),
        d.int("octaves", Noise.OCTAVES),
    )

    /** Refraction index at the d line and Cauchy dispersion of a dielectric's `eta`. */
    fun eta(d: PbrtDirective, warnings: MutableList<String>): Pair<Double, Double> {
        val param = d.params["eta"] ?: return DEFAULT_ETA to 0.0
        return when {
            param.type == "float" -> param.doubles().first() to 0.0
            param.values.firstOrNull() is String -> GLASSES[param.strings().first()] ?: run {
                warnings += "line ${d.line}: spectrum ${param.strings().first()} drawn as glass of 1.5"
                DEFAULT_ETA to 0.0
            }
            else -> cauchy(param.doubles())
        }
    }

    /** The Cauchy fit n = A + B / λ² of a spectrum given as wavelength (nm), index pairs; least squares. */
    fun cauchy(pairs: List<Double>): Pair<Double, Double> {
        val xs = pairs.chunked(2).map { (nm, _) -> 1 / (nm / NM_PER_UM).pow(2) }
        val ns = pairs.chunked(2).map { (_, n) -> n }
        if (xs.size < 2) return (ns.firstOrNull() ?: DEFAULT_ETA) to 0.0
        val meanX = xs.average()
        val meanN = ns.average()
        val b = xs.indices.sumOf { (xs[it] - meanX) * (ns[it] - meanN) } / xs.sumOf { (it - meanX) * (it - meanX) }
        val a = meanN - b * meanX
        return a + b / (D_LINE_UM * D_LINE_UM) to b
    }

    /** krt roughness from pbrt's: pbrt's GGX α is √roughness when remapped, krt's is roughness². */
    fun fromPbrtRoughness(roughness: Double, remap: Boolean): Double =
        if (remap) roughness.pow(QUARTER) else sqrt(roughness)

    /** pbrt roughness (remapped) from krt's, so pbrt draws the same GGX α. */
    fun toPbrtRoughness(roughness: Double): Double = roughness.pow(1 / QUARTER)

    /** The share [part] of [whole], 0 of nothing. */
    fun share(part: Double, whole: Double): Double = if (whole > 0) part / whole else 0.0
}
