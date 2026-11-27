package krt.domain.render

import org.jetbrains.skia.Shader

/**
 * Resource holding the frame the renderer draws into and the listeners that show it on screen:
 * the CPU frame as pixels, the GPU frame as a shader that draws it (ADR 0009).
 */
class FrameBuffer(width: Int, height: Int) {

    @Volatile
    var frame: Frame = Frame(width, height)
        private set

    @Volatile
    private var listener: (Frame) -> Unit = {}

    @Volatile
    private var gpuListener: (Shader) -> Unit = {}

    fun onUpdate(listener: (Frame) -> Unit) {
        this.listener = listener
    }

    fun onGpuUpdate(listener: (Shader) -> Unit) {
        gpuListener = listener
    }

    /** Replaces the frame with an empty one when the size differs. */
    fun resizeTo(width: Int, height: Int) {
        if (width == frame.width && height == frame.height) return
        frame = Frame(width, height)
    }

    fun publish(frame: Frame) {
        listener(frame)
    }

    fun publishGpu(shader: Shader) {
        gpuListener(shader)
    }
}
