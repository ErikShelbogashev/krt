package krt.domain.render

import kotlin.math.abs
import kotlin.math.sqrt

internal data class ContrastError(val delta: Double, val limit: Double) {
    val withinLimit get() = abs(delta) <= limit

    companion object {
        /** Each observation is one pixel; channel contributions from the same paths stay together. */
        fun measure(errors: DoubleArray, weight: Double, floor: Double, deviations: Double): ContrastError? {
            if (weight == 0.0) return null
            require(errors.size > 1)
            val mean = errors.average()
            val sumSquares = errors.sumOf { (it - mean) * (it - mean) }
            val standardError = sqrt(sumSquares * errors.size / (errors.size - 1)) / weight
            return ContrastError(errors.sum() / weight, floor + deviations * standardError)
        }
    }
}
