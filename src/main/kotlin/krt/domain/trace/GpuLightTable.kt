package krt.domain.trace

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToLong
import krt.domain.scene.model.Traceable

/** The section of lights of a [GpuScene] (ADR 0012): a pure transformation, mirrored by the shader's sampling. */
object GpuLightTable {

    /** Whole numbers of 2^24 for shares in [0, 1]. */
    const val SHARE_ONE = (1 shl 24) - 1

    private const val LOG_OFFSET = 64.0
    private const val LOG_STEPS = 131_072.0

    /**
     * The lights and then their members, as the section of lights lays them out; [slots] gives each primitive's
     * slot in the leaves.
     */
    fun codes(emitters: List<Emitter>, slots: Map<Traceable, Int>): IntArray {
        val total = emitters.sumOf { it.weight }
        val shares = emitters.runningFold(0.0) { sum, emitter -> sum + emitter.weight }.drop(1).map { it / total }
        val firsts = emitters.runningFold(0) { sum, emitter -> sum + emitter.members.size }
        val lights = emitters.flatMapIndexed { i, emitter ->
            listOf(share(shares[i]), firsts[i], emitter.members.size, encodeLog(emitter.weight / total / emitter.area))
        }
        val members = emitters.flatMap { emitter ->
            val cumulative = emitter.areas.runningFold(0.0) { sum, area -> sum + area }.drop(1)
            emitter.members.indices.flatMap {
                listOf(slots.getValue(emitter.members[it]), share(cumulative[it] / emitter.area))
            }
        }
        return (lights + members).toIntArray()
    }

    /** A share in [0, 1] as a whole number of 2^24. */
    fun share(value: Double): Int = (value * SHARE_ONE).roundToLong().coerceIn(0L, SHARE_ONE.toLong()).toInt()

    /** Code of a positive [value] by its logarithm: 2^-64 to 2^64, to about a hundred-thousandth of itself. */
    fun encodeLog(value: Double): Int =
        ((log2(value) + LOG_OFFSET) * LOG_STEPS).roundToLong().coerceIn(0L, SHARE_ONE.toLong()).toInt()

    fun decodeLog(code: Int): Double = 2.0.pow(code.toDouble() / LOG_STEPS - LOG_OFFSET)
}
