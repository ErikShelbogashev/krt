package krt.i9ns.textfile

import java.io.File

/** Resource hiding the file system for text reports: writes UTF-8 text to a file. */
class TextFiles {
    fun write(file: File, text: String) {
        file.writeText(text)
    }
}
