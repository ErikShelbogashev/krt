package krt.domain.render

/** Nested square strata for the first 4, 16, 64 and 256 samples of a film pixel. */
internal object PixelStrata {
    private const val LEVELS = 4
    private const val RADIX = 4
    const val SIDE = 1 shl LEVELS
    const val CELLS = SIDE * SIDE

    // Select the low bit of each base-4 digit, starting with the most significant spatial subdivision.
    private val columns = IntArray(CELLS) { index ->
        var digits = index
        var column = 0
        repeat(LEVELS) {
            column = 2 * column + (digits and 1)
            digits /= RADIX
        }
        column
    }

    // Nearest odd integer to CELLS / sqrt(2). Odd strides permute the residues of every power of four.
    private const val STRIDE = 181

    fun cell(pass: Int, shift: Int): Int = (pass * STRIDE + shift) and (CELLS - 1)

    fun offset(index: Int, jitter: Double): Double = (columns[index] + jitter) / SIDE

    /** Multiplicative hashing puts neighboring pixels at different shifts without consuming a path random number. */
    fun shift(frameSeed: Long, index: Int): Int =
        ((frameSeed.hashCode() + index) * GOLDEN_HASH) ushr (Int.SIZE_BITS - 2 * LEVELS)

    private const val GOLDEN_HASH = -1640531527
}
