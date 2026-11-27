package krt.platform.text

import java.util.Locale

/** Numbers typed into a field and shown in it. */
object NumberInput {

    /** The number typed as [text], or null when it is not a number within [range]. */
    fun parse(text: String, range: ClosedFloatingPointRange<Double>): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in range }

    fun format(value: Double, decimals: Int): String = String.format(Locale.ROOT, "%.${decimals}f", value)
}
