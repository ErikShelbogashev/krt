package krt.domain.render.model

import kotlin.test.Test
import kotlin.test.assertEquals

class RenderStatusTest {

    @Test
    fun `failed GPU readback is visible in the status`() {
        val failure = RenderProgress.NONE.copy(failure = "Could not read the GPU frame")
        assertEquals("Render failed · Could not read the GPU frame", RenderStatus.progressText(RenderMode.GPU, failure))
    }

    private val inWork = RenderProgress(passes = 5, completedPasses = 2, passFraction = 0.4, elapsedMs = 1234)

    @Test
    fun `progress names the pass in work, then the finished frame`() {
        assertEquals("Pass 3 / 5", RenderStatus.progressText(RenderMode.CPU, inWork))
        assertEquals("Done · 5/5 passes", RenderStatus.progressText(RenderMode.CPU, RenderProgress.finished(5, 10)))
        val wireframe = RenderProgress.finished(1, 0)
        assertEquals("Wireframe · ready", RenderStatus.progressText(RenderMode.WIREFRAME, wireframe))
    }

    @Test
    fun `elapsed time in seconds with one decimal`() {
        assertEquals("1.2 s", RenderStatus.elapsedText(inWork))
    }

    @Test
    fun `engine names the renderer, its threads and the bounce depth`() {
        assertEquals("CPU · 8 threads · depth 7", RenderStatus.engineText(RenderMode.CPU, 8, 7))
        assertEquals("GPU · Skia SkSL · depth 7", RenderStatus.engineText(RenderMode.GPU, 8, 7))
    }

    @Test
    fun `a quick look says so`() {
        val quickLook = RenderProgress(5, 0, 0.0, 0, isDraft = true)
        assertEquals("Quick look · 5 passes to go", RenderStatus.progressText(RenderMode.CPU, quickLook))
    }
}
