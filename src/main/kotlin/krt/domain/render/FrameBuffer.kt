package krt.domain.render

import java.awt.image.BufferedImage

/**
 * Resource holding the image the renderer draws into and the listener that shows it on screen.
 */
class FrameBuffer(width: Int, height: Int) {

    @Volatile
    var image: BufferedImage = newImage(width, height)
        private set

    @Volatile
    private var listener: (BufferedImage) -> Unit = {}

    fun onUpdate(listener: (BufferedImage) -> Unit) {
        this.listener = listener
    }

    /** Replaces the image with an empty one when the size differs. */
    fun resizeTo(width: Int, height: Int) {
        if (width == image.width && height == image.height) return
        image = newImage(width, height)
    }

    fun publish(image: BufferedImage) {
        listener(image)
    }

    private companion object {
        fun newImage(width: Int, height: Int) = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    }
}
