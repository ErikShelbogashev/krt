package krt.platform.layout

import kotlin.math.min

/** How a frame is scaled in the viewport. */
enum class FrameScaling {
    /** As large as fits, keeping the aspect ratio. */
    FIT,

    /** One frame pixel per screen pixel. */
    ACTUAL,
}

/** A frame placed in a box: its top-left corner at ([left], [top]) and [scale] screen pixels per frame pixel. */
data class FramePlacement(val left: Float, val top: Float, val scale: Float) {

    /** Column of the frame pixel under box x coordinate [x]; outside the frame too. */
    fun frameX(x: Float): Int = ((x - left) / scale).toInt()

    /** Row of the frame pixel under box y coordinate [y]; outside the frame too. */
    fun frameY(y: Float): Int = ((y - top) / scale).toInt()
}

/** Places a frame of one size into a box of another, centered. Sizes in pixels. */
object FrameFit {

    /** Keeps the scale positive for an empty box, so coordinates map back to the frame. */
    private const val MIN_SCALE = 1e-3f

    fun place(frameWidth: Int, frameHeight: Int, boxWidth: Int, boxHeight: Int, scaling: FrameScaling): FramePlacement {
        val scale = when (scaling) {
            FrameScaling.FIT -> min(boxWidth.toFloat() / frameWidth, boxHeight.toFloat() / frameHeight)
            FrameScaling.ACTUAL -> 1f
        }.coerceAtLeast(MIN_SCALE)
        return FramePlacement((boxWidth - frameWidth * scale) / 2, (boxHeight - frameHeight * scale) / 2, scale)
    }
}
