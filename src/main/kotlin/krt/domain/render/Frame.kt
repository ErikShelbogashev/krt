package krt.domain.render

/**
 * Pixels of one frame as packed ARGB ints, row by row from the top-left corner.
 * Render workers write it, the UI reads it to show it on screen (ADR 0008).
 */
class Frame(val width: Int, val height: Int) {
    val pixels = IntArray(width * height)
}
