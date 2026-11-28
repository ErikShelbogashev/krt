package krt.domain.render.model

/**
 * What a flight of the benchmark records, window frame by window frame: the frame times and the progress of the
 * frame on screen when the camera moved on. Timestamps are the window's frame times in nanoseconds.
 */
data class FlightRecording(
    val startNanos: Long,
    val lastNanos: Long,
    val intervalsMs: List<Double> = emptyList(),
    val shown: List<RenderProgress> = emptyList(),
) {
    /** One more window frame at [nanos]; the frame on screen had [progress] when the camera moved on. */
    fun frame(nanos: Long, progress: RenderProgress): FlightRecording =
        copy(lastNanos = nanos, intervalsMs = intervalsMs + (nanos - lastNanos) / NS_PER_MS, shown = shown + progress)

    val poses: Int get() = intervalsMs.size

    /** Window frames per second so far. */
    val fps: Double get() = if (lastNanos > startNanos) poses * NS_PER_S / (lastNanos - startNanos) else 0.0

    fun sample(settleMs: Double?, stopped: Boolean) =
        BenchmarkSample(intervalsMs, shown, (lastNanos - startNanos) / NS_PER_MS, settleMs, stopped)

    companion object {
        fun startingAt(nanos: Long) = FlightRecording(nanos, nanos)
    }
}

/** Waits for the frame on screen to be done, from the window frame at [startNanos], for at most [timeoutMs]. */
data class DoneWatch(val startNanos: Long, val timeoutMs: Double = DONE_TIMEOUT_MS) {

    /** Milliseconds from the start to the window frame at [nanos]. */
    fun elapsedMs(nanos: Long): Double = (nanos - startNanos) / NS_PER_MS

    /** Whether to keep waiting at the window frame at [nanos], with [progress] on screen. */
    fun waiting(nanos: Long, progress: RenderProgress, stopped: Boolean): Boolean =
        !stopped && progress.failure == null && !progress.isDone && elapsedMs(nanos) <= timeoutMs

    /** The time it took, or null when the watch was stopped or gave up. */
    fun result(nanos: Long, progress: RenderProgress, stopped: Boolean): Double? =
        elapsedMs(nanos).takeIf { progress.isDone && !stopped }

    private companion object {
        /** How long a run may take to finish its frame before or after its flight. */
        const val DONE_TIMEOUT_MS = 120_000.0
    }
}

private const val NS_PER_MS = 1e6
private const val NS_PER_S = 1e9
