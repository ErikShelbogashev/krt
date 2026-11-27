package krt.bench

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import java.util.concurrent.TimeUnit
import krt.platform.skia.ArgbBitmapConverter
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State

/** Cost of handing a finished frame to Compose, paid on the UI thread at every repaint. */
@Suppress("unused") // JMH finds and runs the benchmarks by reflection
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class DisplayBenchmark {

    @Param("400", "1024")
    var size: Int = 0

    private lateinit var image: BufferedImage
    private lateinit var pixels: IntArray
    private val converter = ArgbBitmapConverter()

    @Setup
    fun setUp() {
        image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        pixels = IntArray(size * size)
    }

    /** The way frames were shown before ADR 0008, kept for comparison. */
    @Benchmark
    fun toComposeImageBitmap(): ImageBitmap = image.toComposeImageBitmap()

    /**
     * How the app shows a frame now. The benchmark frees each bitmap right away: at thousands of calls
     * a second native memory would outrun the garbage collector. The app converts at most once per repaint.
     */
    @Benchmark
    fun argbToImageBitmap(): Int {
        val bitmap = converter.toImageBitmap(pixels, size, size)
        val width = bitmap.width
        bitmap.asSkiaBitmap().close()
        return width
    }
}
