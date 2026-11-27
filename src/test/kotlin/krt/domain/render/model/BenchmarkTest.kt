package krt.domain.render.model

import kotlin.test.Test
import kotlin.test.assertEquals
import krt.domain.scene.model.DemoScene

class BenchmarkTest {

    private val config = BenchmarkConfig(DemoScene.SHOWCASE, RenderMode.CPU, 400, 400)

    @Test
    fun `percentiles take the nearest rank`() {
        val sorted = (1..100).map { it.toDouble() }
        assertEquals(50.0, Benchmark.percentile(sorted, 0.50))
        assertEquals(95.0, Benchmark.percentile(sorted, 0.95))
        assertEquals(100.0, Benchmark.percentile(sorted, 1.0))
        assertEquals(0.0, Benchmark.percentile(emptyList(), 0.5))
    }

    @Test
    fun `a run counts frames, stalls and how complete the frame on screen was`() {
        val intervals = List(8) { 10.0 } + listOf(60.0, 120.0)
        val progress = listOf(
            RenderProgress(5, 0, 0.5, 0), RenderProgress(5, 2, 0.0, 0),
            RenderProgress(5, 0, 0.0, 0), RenderProgress(5, 5, 0.0, 0),
        )
        val result = Benchmark.summarize(config, BenchmarkSample(intervals, progress, 260.0, 1500.0, stopped = false))
        assertEquals(10, result.poses)
        assertEquals(10 * 1000.0 / 260.0, result.fps, 1e-9)
        val times = FrameTimes(p50 = 10.0, p95 = 120.0, p99 = 120.0, max = 120.0, stalls50 = 2, stalls100 = 1)
        assertEquals(times, result.frames)
        assertEquals((0.5 + 2 + 0 + 5) / 4, result.shown.meanPasses, 1e-9)
        assertEquals(0.5, result.shown.fullShare)
    }

    @Test
    fun `the plan runs every combination`() {
        val plan = BenchmarkConfig.plan(DemoScene.entries, RenderMode.entries.take(2), listOf(400 to 400, 1024 to 1024))
        assertEquals(2 * 2 * 2, plan.size)
        assertEquals(BenchmarkConfig(DemoScene.SHOWCASE, RenderMode.CPU, 400, 400), plan.first())
    }

    @Test
    fun `reports have a row per run`() {
        val result = Benchmark.summarize(config, BenchmarkSample(listOf(10.0), emptyList(), 10.0, null, stopped = true))
        val markdown = Benchmark.markdown(listOf(result)).lines()
        assertEquals(3, markdown.size)
        assertEquals("| Showcase | CPU path tracing | 400×400 |", markdown[2].substringBefore(" | 100.0").plus(" |"))
        assertEquals(2, Benchmark.csv(listOf(result)).lines().size)
        assertEquals("stopped", Benchmark.cells(result).last())
    }
}
