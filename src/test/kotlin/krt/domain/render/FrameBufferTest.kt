package krt.domain.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import krt.domain.render.model.RenderProgress

class FrameBufferTest {
    @Test
    fun `a new pose cannot finish its benchmark with the previous pose's progress`() {
        val buffer = FrameBuffer(16, 16)
        val first = buffer.beginFrame()
        buffer.publish(first, RenderProgress.finished(5, 10))
        assertSame(first, buffer.beginFrame())
        assertEquals(RenderProgress.NONE, buffer.progress)
    }
}
