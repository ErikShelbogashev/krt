package krt.platform.layout

import kotlin.test.Test
import kotlin.test.assertEquals

class FrameFitTest {

    @Test
    fun `fit scales the frame up to the shorter side of the box and centers it`() {
        val placement = FrameFit.place(400, 200, 1000, 800, FrameScaling.FIT)
        assertEquals(FramePlacement(left = 0f, top = 150f, scale = 2.5f), placement)
    }

    @Test
    fun `fit scales a frame larger than the box down`() {
        val placement = FrameFit.place(1024, 1024, 512, 600, FrameScaling.FIT)
        assertEquals(FramePlacement(left = 0f, top = 44f, scale = 0.5f), placement)
    }

    @Test
    fun `actual size keeps one frame pixel per screen pixel, centered and cropped by the box`() {
        assertEquals(FramePlacement(300f, 200f, 1f), FrameFit.place(400, 400, 1000, 800, FrameScaling.ACTUAL))
        assertEquals(FramePlacement(-12f, -12f, 1f), FrameFit.place(1024, 1024, 1000, 1000, FrameScaling.ACTUAL))
    }

    @Test
    fun `box coordinates map back to frame pixels at any scale`() {
        val placement = FrameFit.place(400, 200, 1000, 800, FrameScaling.FIT)
        assertEquals(0, placement.frameX(0f))
        assertEquals(399, placement.frameX(999f))
        assertEquals(40, placement.frameY(250f))
        assertEquals(40, placement.frameX(100f) - placement.frameX(0f))
    }

    @Test
    fun `an empty box keeps a positive scale`() {
        val placement = FrameFit.place(400, 400, 0, 0, FrameScaling.FIT)
        assertEquals(true, placement.scale > 0f)
    }
}
