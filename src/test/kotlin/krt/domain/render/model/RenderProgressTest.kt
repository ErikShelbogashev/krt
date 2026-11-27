package krt.domain.render.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RenderProgressTest {

    @Test
    fun `a pass in work counts its done share`() {
        val progress = RenderProgress(passes = 4, completedPasses = 1, passFraction = 0.5, elapsedMs = 10)
        assertFalse(progress.isDone)
        assertEquals(2, progress.currentPass)
        assertEquals(0.375, progress.fraction)
    }

    @Test
    fun `a finished frame is done at its last pass`() {
        val progress = RenderProgress.finished(passes = 5, elapsedMs = 100)
        assertTrue(progress.isDone)
        assertEquals(5, progress.currentPass)
        assertEquals(1.0, progress.fraction)
    }

    @Test
    fun `before the first frame nothing is done`() {
        assertFalse(RenderProgress.NONE.isDone)
        assertEquals(0.0, RenderProgress.NONE.fraction)
    }

    @Test
    fun `a quick look covers the whole frame, a pass in work only its share`() {
        assertEquals(1.0, RenderProgress(5, 0, 0.0, 0, isDraft = true).coverage)
        assertEquals(0.3, RenderProgress(5, 0, 0.3, 0).coverage)
        assertEquals(1.0, RenderProgress(5, 2, 0.1, 0).coverage)
    }
}
