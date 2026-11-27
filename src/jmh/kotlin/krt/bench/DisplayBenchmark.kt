package krt.bench

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State

/** Cost of handing a finished frame to Compose, paid on the UI thread at every repaint. */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class DisplayBenchmark {

    @Param("400", "1024")
    var size: Int = 0

    private lateinit var image: BufferedImage

    @Setup
    fun setUp() {
        image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    }

    @Benchmark
    fun toComposeImageBitmap(): ImageBitmap = image.toComposeImageBitmap()
}
