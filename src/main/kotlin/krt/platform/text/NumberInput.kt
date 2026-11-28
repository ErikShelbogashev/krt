package krt.platform.text

import java.util.Locale

/** Numbers typed into a field and shown in it. */
object NumberInput {

    /** The number typed as [text], or null when it is not a number within [range]. */
    fun parse(text: String, range: ClosedFloatingPointRange<Double>): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in range }

    /** An editable field must distinguish very small physical coefficients from zero. */
    fun editable(value: Double, decimals: Int): String {
        val rounded = format(value, decimals)
        return if (rounded.length <= MAX_FIELD_LENGTH && rounded.toDouble() == value) rounded else value.toString()
    }

    private const val MAX_FIELD_LENGTH = 12

    fun format(value: Double, decimals: Int): String = String.format(Locale.ROOT, "%.${decimals}f", value)
}
