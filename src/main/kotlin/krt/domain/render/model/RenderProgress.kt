package krt.domain.render.model

/**
 * How far the frame on screen is: [completedPasses] of [passes] are done, the pass in work is [passFraction] done,
 * [elapsedMs] since the frame started. A draft ([isDraft]) is the quick look before the passes: the whole frame at
 * a lower resolution.
 */
data class RenderProgress(
    val passes: Int,
    val completedPasses: Int,
    val passFraction: Double,
    val elapsedMs: Long,
    val isDraft: Boolean = false,
) {
    val isDone: Boolean get() = completedPasses >= passes

    /** Share of the frame's work done, from 0 to 1. */
    val fraction: Double get() = if (isDone) 1.0 else (completedPasses + passFraction) / passes

    /** Number of the pass in work, counting from 1; the last pass once the frame is done. */
    val currentPass: Int get() = minOf(completedPasses + 1, passes)

    /** Share of the frame that shows the current view: all of it from the quick look on, else the pass so far. */
    val coverage: Double get() = if (isDraft || completedPasses > 0) 1.0 else passFraction

    companion object {
        /** Before the first frame. */
        val NONE = RenderProgress(passes = 1, completedPasses = 0, passFraction = 0.0, elapsedMs = 0)

        fun finished(passes: Int, elapsedMs: Long) = RenderProgress(passes, passes, 0.0, elapsedMs)
    }
}
