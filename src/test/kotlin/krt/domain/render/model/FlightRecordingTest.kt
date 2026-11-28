package krt.domain.render.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlightRecordingTest {

    private val moving = RenderProgress(5, 0, 0.2, 0)
    private val done = RenderProgress.finished(5, 0)

    @Test
    fun `a recording keeps frame times and the progress shown, and counts frames per second`() {
        val recording = FlightRecording.startingAt(1_000_000_000)
            .frame(1_010_000_000, moving)
            .frame(1_020_000_000, done)
        assertEquals(listOf(10.0, 10.0), recording.intervalsMs)
        assertEquals(listOf(moving, done), recording.shown)
        assertEquals(100.0, recording.fps, 1e-9)
        val sample = recording.sample(settleMs = 250.0, stopped = false)
        assertEquals(20.0, sample.durationMs, 1e-9)
        assertEquals(250.0, sample.settleMs)
    }

    @Test
    fun `a done watch waits until the frame is done, stopped or timed out`() {
        val watch = DoneWatch(startNanos = 0, timeoutMs = 100.0)
        assertTrue(watch.waiting(50_000_000, moving, stopped = false))
        val failed = moving.copy(failure = "GPU readback failed")
        assertEquals(false, watch.waiting(50_000_000, failed, stopped = false))
        assertNull(watch.result(50_000_000, failed, stopped = false))
        assertEquals(false, watch.waiting(50_000_000, done, stopped = false))
        assertEquals(false, watch.waiting(50_000_000, moving, stopped = true))
        assertEquals(false, watch.waiting(150_000_000, moving, stopped = false))
        assertEquals(50.0, watch.result(50_000_000, done, stopped = false))
        assertNull(watch.result(150_000_000, moving, stopped = false))
    }
}
