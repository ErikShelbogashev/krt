package krt.domain.render

import kotlin.math.ceil
import kotlin.math.sqrt

/** Pure calculations of the progressive GPU frame (ADR 0009): its quick look and its steps. */
object GpuSampling {

    /**
     * Samples per pixel of each step of a progressive GPU frame of [samples] samples: one, then as many as all steps
     * before took, so a noisy frame comes at once and few steps refine it, but never more than [maxStep] at once,
     * so no step keeps the GPU busy for long.
     */
    fun steps(samples: Int, maxStep: Int = GPU_STEP_SAMPLES): List<Int> {
        val steps = mutableListOf<Int>()
        var taken = 0
        while (taken < samples) {
            val step = minOf(maxOf(taken, 1), maxStep, samples - taken)
            steps += step
            taken += step
        }
        return steps
    }

    /**
     * How many times smaller each way the quick first look of a GPU frame is, so it has at most [budget] pixels:
     * the first look runs inside a window repaint, and a camera dragged every repaint must not slow the window.
     */
    fun draftScale(width: Int, height: Int, budget: Int = GPU_PREVIEW_PIXELS): Int =
        ceil(sqrt(width.toDouble() * height / budget)).toInt().coerceAtLeast(1)

    /** One side of the quick look of a frame [size] pixels across, [scale] times smaller, rounded up. */
    fun draftSize(size: Int, scale: Int): Int = (size + scale - 1) / scale

    /** Pixels of the quick first look of a GPU frame: a few milliseconds of GPU time in a busy scene. */
    private const val GPU_PREVIEW_PIXELS = 160_000

    /** Samples per pixel a GPU step takes at most: about 80 ms of GPU time for a 1024×1024 frame. */
    private const val GPU_STEP_SAMPLES = 4
}
