package krt.domain.render

import krt.domain.render.model.RenderMode

/**
 * Pixels of one frame as packed ARGB ints, row by row from the top-left corner.
 * Render workers write it, the UI reads it to show it on screen (ADR 0008).
 */
class Frame private constructor(val width: Int, val height: Int, buffer: IntArray) {
    constructor(width: Int, height: Int) : this(width, height, IntArray(width * height))

    var pixels: IntArray = buffer
        private set

    private var cpuWriter: Frame? = null

    /** CPU workers finish cancelled blocks asynchronously. Keep their array separate once another mode starts. */
    internal fun destination(mode: RenderMode): Frame {
        if (mode == RenderMode.CPU) {
            return cpuWriter ?: Frame(width, height, pixels).also { cpuWriter = it }
        }
        if (cpuWriter != null) {
            pixels = IntArray(width * height)
            cpuWriter = null
        }
        return this
    }
}
