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
            val step = nextStep(taken, samples, maxStep)
            steps += step
            taken += step
        }
        return steps
    }

    /** Samples of the step after [taken] of [samples], as [steps] takes them; 0 when the frame is done. */
    fun nextStep(taken: Int, samples: Int, maxStep: Int): Int = minOf(maxOf(taken, 1), maxStep, samples - taken)

    /**
     * How much of a pass a GPU step takes at most: [samples] per pixel over one of [bands] bands of rows, adapted to
     * the window frames that drew steps. A step runs inside a window repaint, and steps that take longer than the
     * repaint interval queue up on the GPU, so every window frame waits behind them, and a camera moved meanwhile
     * gets its frames late. A step frame over [STEP_FRAME_TARGET_MS] cuts the work in proportion: the rows first,
     * in more bands, and the samples only once the bands are as thin as they get; but a single one less than
     * [HICCUP_LOAD] times over, a hiccup of the window, cuts nothing until a second one in a row follows. After
     * [STEPS_TO_GROW] step frames in a row that would still fit with twice the rows or one more sample, it grows
     * back by that.
     *
     * Narrower bands retain the samples per pixel while reducing the time spent in a single draw.
     */
    data class StepBudget(
        val samples: Int = GPU_STEP_SAMPLES,
        val bands: Int = 1,
        val fitStreak: Int = 0,
        val slow: Boolean = false,
    ) {
        fun after(frameMs: Double): StepBudget {
            val load = frameMs / STEP_FRAME_TARGET_MS
            return when {
                load > 1 && load < HICCUP_LOAD && !slow -> copy(fitStreak = 0, slow = true)
                load > 1 && bands < MAX_BANDS -> StepBudget(samples, ceil(bands * load).toInt().coerceAtMost(MAX_BANDS))
                load > 1 -> StepBudget((samples / load).toInt().coerceAtLeast(1), bands)
                !fitsMore(load) -> copy(fitStreak = 0, slow = false)
                fitStreak + 1 < STEPS_TO_GROW -> copy(fitStreak = fitStreak + 1, slow = false)
                bands > 1 -> StepBudget(samples, (bands + 1) / 2)
                else -> StepBudget(samples + 1, 1)
            }
        }

        private fun fitsMore(load: Double): Boolean =
            if (bands > 1) load * 2 <= 1 else samples < GPU_STEP_SAMPLES && load * (samples + 1) / samples <= 1

        companion object {
            /**
             * The budget a scene starts with: the full samples over thin bands. The first steps of a scene whose
             * cost nobody knows yet must not freeze the window. It grows no faster than any budget: the window's
             * frame times trail the GPU's work by a few frames, and a budget that outgrows them queues steps the
             * GPU stalls on, up to Metal dropping the window's commands.
             */
            val CAUTIOUS = StepBudget(GPU_STEP_SAMPLES, CAUTIOUS_BANDS)
        }
    }

    /** The step budget for a frame of a scene: [CAUTIOUS][StepBudget.CAUTIOUS] for a scene not drawn before. */
    fun stepBudgetFor(newScene: Boolean, budget: StepBudget): StepBudget = if (newScene) StepBudget.CAUTIOUS else budget

    /** Samples per pixel the whole frame [height] rows high has after a band of a step of [samples] after [taken]. */
    fun samplesDone(taken: Int, samples: Int, band: IntRange, height: Int): Int =
        if (band.last >= height - 1) taken + samples else taken

    /** Bands a pass of a frame [height] rows high takes under [budget]: no more than it has rows. */
    fun bandsOf(budget: StepBudget, height: Int): Int = budget.bands.coerceIn(1, maxOf(1, height))

    /** The next band of a frame [height] rows high, from row [from], one of [bands] in height. */
    fun nextRows(from: Int, height: Int, bands: Int): IntRange =
        from until minOf(height, from + (height + bands - 1) / bands)

    /** Rows of band [band] of [bands] of a frame [height] rows high, from the top of the window. */
    fun bandRows(height: Int, band: Int, bands: Int): IntRange = height * band / bands until height * (band + 1) / bands

    /** Bands a pass of a GPU frame splits into at most: a 1024-row frame still gets 16 rows per step. */
    private const val MAX_BANDS = 64

    /** Bands of a pass of a scene not drawn before. */
    private const val CAUTIOUS_BANDS = 16

    /** Window frame a GPU step should fit in: under the repaint interval that paces the steps. */
    private const val STEP_FRAME_TARGET_MS = 40.0

    /** A step frame up to this many times over its target may be a hiccup of the window, once. */
    private const val HICCUP_LOAD = 2.0

    /** Step frames in a row that must fit before the step budget grows. */
    private const val STEPS_TO_GROW = 4

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
         * shrinks it at once in proportion, by at most a quarter, or all the way when it is [FAR_OVER] times over,
         * as in a scene far heavier than the last; it grows by a quarter only after [STREAK_TO_GROW] quick ones in a
         * row. Switching the size of quick looks every frame stalls Skia, so it must not swing.
         */
        fun after(frameMs: Double): DraftBudget = when {
            frameMs > DRAFT_FRAME_TARGET_MS * FAR_OVER -> DraftBudget(scaled(DRAFT_FRAME_TARGET_MS / frameMs), 0)
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

    /** Pixels of the quick first look at least: about a 64×64 frame, for the heaviest scenes. */
    private const val MIN_DRAFT_PIXELS = 4_096

    /** A quick look this many times over its target shrinks the next one all the way in proportion. */
    private const val FAR_OVER = 4.0

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
