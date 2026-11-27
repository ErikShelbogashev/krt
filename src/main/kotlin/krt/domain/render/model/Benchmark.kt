package krt.domain.render.model

import java.util.Locale
import kotlin.math.ceil
import krt.domain.scene.model.DemoScene

/** One run of the flight benchmark: a scene, a renderer and a frame size. */
data class BenchmarkConfig(val scene: DemoScene, val mode: RenderMode, val width: Int, val height: Int) {
    companion object {
        /** Poses of one flight: one per window frame, about ten seconds at 60 Hz, five at 120 Hz. */
        const val STEPS = 600
        const val SAMPLES = 5
        const val DEPTH = 7

        /** Every combination of the chosen scenes, modes and sizes, in a stable order. */
        fun plan(scenes: List<DemoScene>, modes: List<RenderMode>, sizes: List<Pair<Int, Int>>): List<BenchmarkConfig> =
            scenes.flatMap { scene ->
                sizes.flatMap { (w, h) -> modes.map { mode -> BenchmarkConfig(scene, mode, w, h) } }
            }
    }
}

/** What one flight measured: window frames, how far the frame on screen was when the camera moved on, and the end. */
data class BenchmarkSample(
    val frameIntervalsMs: List<Double>,
    val shownProgress: List<RenderProgress>,
    val durationMs: Double,
    val settleMs: Double?,
    val stopped: Boolean,
)

/** The figures of one run, as the results table shows them. */
data class BenchmarkResult(
    val config: BenchmarkConfig,
    val poses: Int,
    val durationMs: Double,
    val fps: Double,
    val frames: FrameTimes,
    val shown: ShownQuality,
    val settleMs: Double?,
    val stopped: Boolean,
)

/** Window frame times in ms: percentiles, the longest, and how many took over 50 and over 100 ms. */
data class FrameTimes(
    val p50: Double,
    val p95: Double,
    val p99: Double,
    val max: Double,
    val stalls50: Int,
    val stalls100: Int,
)

/**
 * How complete the frame on screen was each time the camera moved on: passes on average (with the share of the
 * pass in work) and the share of frames past their first, quick look.
 */
data class ShownQuality(val meanPasses: Double, val fullShare: Double)

/** Pure arithmetic of the flight benchmark: percentiles, stalls and reports. */
object Benchmark {

    private const val STALL_MS = 50.0
    private const val LONG_STALL_MS = 100.0
    private const val MS_PER_S = 1000.0
    private const val P50 = 0.50
    private const val P95 = 0.95
    private const val P99 = 0.99
    private const val PERCENT = 100

    fun summarize(config: BenchmarkConfig, sample: BenchmarkSample): BenchmarkResult {
        val intervals = sample.frameIntervalsMs
        val sorted = intervals.sorted()
        val progress = sample.shownProgress
        return BenchmarkResult(
            config = config,
            poses = intervals.size,
            durationMs = sample.durationMs,
            fps = if (sample.durationMs > 0) intervals.size * MS_PER_S / sample.durationMs else 0.0,
            frames = FrameTimes(
                p50 = percentile(sorted, P50),
                p95 = percentile(sorted, P95),
                p99 = percentile(sorted, P99),
                max = sorted.lastOrNull() ?: 0.0,
                stalls50 = intervals.count { it > STALL_MS },
                stalls100 = intervals.count { it > LONG_STALL_MS },
            ),
            shown = ShownQuality(
                meanPasses = progress.map { it.completedPasses + it.passFraction }.average().orZero(),
                fullShare = progress.count { it.completedPasses > 0 }.toDouble() / progress.size.coerceAtLeast(1),
            ),
            settleMs = sample.settleMs,
            stopped = sample.stopped,
        )
    }

    /** The value below which [share] of the [sorted] values lie (nearest rank). */
    fun percentile(sorted: List<Double>, share: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val rank = ceil(share * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    private val COLUMNS = listOf(
        "Scene", "Mode", "Size", "FPS", "p50 ms", "p95 ms", "p99 ms", "max ms",
        ">50 ms", ">100 ms", "Passes shown", "Full frames", "Done after",
    )

    fun markdown(results: List<BenchmarkResult>): String =
        (listOf(COLUMNS, COLUMNS.map { "---" }) + results.map(::cells))
            .joinToString("\n") { row -> row.joinToString(" | ", "| ", " |") }

    fun csv(results: List<BenchmarkResult>): String =
        (listOf(COLUMNS) + results.map(::cells)).joinToString("\n") { row -> row.joinToString(",") }

    /** The cells of [result] in the order of the table's columns. */
    fun cells(result: BenchmarkResult): List<String> = with(result) {
        listOf(
            config.scene.title, config.mode.title, "${config.width}×${config.height}",
            f(fps, 1), f(frames.p50, 1), f(frames.p95, 1), f(frames.p99, 1), f(frames.max, 1),
            "${frames.stalls50}", "${frames.stalls100}",
            f(shown.meanPasses, 2), "${(shown.fullShare * PERCENT).toInt()} %",
            settleMs?.let { "${f(it / MS_PER_S, 2)} s" } ?: if (stopped) "stopped" else "—",
        )
    }

    private fun f(value: Double, decimals: Int) = String.format(Locale.ROOT, "%.${decimals}f", value)

    private fun Double.orZero() = if (isNaN()) 0.0 else this
}
