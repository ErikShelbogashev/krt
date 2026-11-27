package krt.i9ns.png

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Resource hiding the file system for images: writes packed ARGB pixels, row by row, as an opaque PNG. */
class PngFiles {

    fun write(file: File, pixels: IntArray, width: Int, height: Int) {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, width, height, pixels, 0, width)
        check(ImageIO.write(image, FORMAT, file)) { "no $FORMAT writer" }
    }

    private companion object {
        const val FORMAT = "png"
    }
}
