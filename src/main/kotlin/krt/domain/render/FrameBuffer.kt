package krt.domain.render

/**
 * Resource holding the frame the renderer draws into and the listener that shows it on screen.
 */
class FrameBuffer(width: Int, height: Int) {

    @Volatile
    var frame: Frame = Frame(width, height)
        private set

    @Volatile
    private var listener: (Frame) -> Unit = {}

    fun onUpdate(listener: (Frame) -> Unit) {
        this.listener = listener
    }

    /** Replaces the frame with an empty one when the size differs. */
    fun resizeTo(width: Int, height: Int) {
        if (width == frame.width && height == frame.height) return
        frame = Frame(width, height)
    }

    fun publish(frame: Frame) {
        listener(frame)
    }
}
