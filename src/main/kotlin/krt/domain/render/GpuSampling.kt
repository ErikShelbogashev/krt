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
    fun draftScale(width: Int, height: Int, budget: Int = MAX_DRAFT_PIXELS): Int =
        ceil(sqrt(width.toDouble() * height / budget)).toInt().coerceAtLeast(1)

    /**
     * Pixels of the GPU quick look, [pixels], and how many quick looks in a row since the last change fit in their
     * window frame, [quickStreak].
     */
    data class DraftBudget(val pixels: Int = MAX_DRAFT_PIXELS, val quickStreak: Int = 0) {

        /**
         * The budget after a quick look took a window frame of [frameMs]. A slow one, over [DRAFT_FRAME_TARGET_MS],
         * shrinks it at once in proportion, by at most a quarter; it grows by a quarter only after [STREAK_TO_GROW]
         * quick ones in a row. Switching the size of quick looks every frame stalls Skia, so it must not swing.
         */
        fun after(frameMs: Double): DraftBudget = when {
            frameMs > DRAFT_FRAME_TARGET_MS ->
                DraftBudget(scaled((DRAFT_FRAME_TARGET_MS / frameMs).coerceAtLeast(MAX_SHRINK)), 0)
            quickStreak + 1 >= STREAK_TO_GROW -> DraftBudget(scaled(GROWTH), 0)
            else -> copy(quickStreak = quickStreak + 1)
        }

        private fun scaled(factor: Double) = (pixels * factor).toInt().coerceIn(MIN_DRAFT_PIXELS, MAX_DRAFT_PIXELS)
    }

    /** One side of the quick look of a frame [size] pixels across, [scale] times smaller, rounded up. */
    fun draftSize(size: Int, scale: Int): Int = (size + scale - 1) / scale

    /** Pixels of the quick first look of a GPU frame at most; the window frame it takes sets how many it gets. */
    const val MAX_DRAFT_PIXELS = 160_000

    /** Pixels of the quick first look at least: about a 200×200 frame. */
    private const val MIN_DRAFT_PIXELS = 40_000

    /**
     * Window frame a quick look should fit in: about 6 ms of GPU time over the rest of a 120 Hz frame,
     * just over one display refresh, so drafts keep up with a camera moved every refresh.
     */
    private const val DRAFT_FRAME_TARGET_MS = 10.0
    private const val MAX_SHRINK = 0.75
    private const val GROWTH = 1.25

    /** Quick looks in a row that must fit before the budget grows: about half a second of dragging. */
    private const val STREAK_TO_GROW = 60

    /** Samples per pixel a GPU step takes at most: about 80 ms of GPU time for a 1024×1024 frame. */
    private const val GPU_STEP_SAMPLES = 4
}
