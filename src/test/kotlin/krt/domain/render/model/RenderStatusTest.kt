package krt.domain.render.model

import kotlin.test.Test
import kotlin.test.assertEquals

class RenderStatusTest {

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
}
