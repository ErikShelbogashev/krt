package krt.domain.render

import kotlin.test.Test
import kotlin.test.assertEquals

class GpuSamplingTest {

    private val full = GpuSampling.DraftBudget()

    @Test
    fun `a slow quick look shrinks the budget at once, by at most a quarter`() {
        assertEquals(GpuSampling.MAX_DRAFT_PIXELS * 10 / 12, full.after(12.0).pixels)
        assertEquals((GpuSampling.MAX_DRAFT_PIXELS * 0.75).toInt(), full.after(40.0).pixels)
        var budget = full
        repeat(50) { budget = budget.after(40.0) }
        assertEquals(40_000, budget.pixels)
    }

    @Test
    fun `the budget grows back only after a run of quick looks`() {
        var budget = full.after(40.0)
        val shrunk = budget.pixels
        repeat(59) { budget = budget.after(8.0) }
        assertEquals(shrunk, budget.pixels)
        budget = budget.after(8.0)
        assertEquals((shrunk * 1.25).toInt(), budget.pixels)
        assertEquals(0, budget.quickStreak)
    }

    @Test
    fun `a full budget stays full`() {
        var budget = full
        repeat(200) { budget = budget.after(8.0) }
        assertEquals(GpuSampling.MAX_DRAFT_PIXELS, budget.pixels)
    }

    @Test
    fun `a smaller budget makes the quick look of a 400 by 400 frame smaller`() {
        assertEquals(1, GpuSampling.draftScale(400, 400, GpuSampling.MAX_DRAFT_PIXELS))
        assertEquals(2, GpuSampling.draftScale(400, 400, 100_000))
        assertEquals(200, GpuSampling.draftSize(400, 2))
        assertEquals(342, GpuSampling.draftSize(1024, 3))
    }
}
