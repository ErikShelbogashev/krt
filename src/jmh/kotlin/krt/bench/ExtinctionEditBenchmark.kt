package krt.bench

import java.util.concurrent.TimeUnit
import krt.domain.scene.World
import krt.domain.scene.model.Fog
import krt.domain.scene.model.Medium
import krt.domain.scene.model.ObjectChange
import krt.platform.math.Point3
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State

/** Latency paid by the inspector when editing a medium that surrounds many independent cavities. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
open class ExtinctionEditBenchmark {
    @Param("1024", "4096")
    var cavities = 0

    @Param("true", "false")
    var firstEdit = false

    private lateinit var parts: List<Fog>
    private lateinit var world: World
    private var alternate = false

    @Setup
    fun prepareScene() {
        val outerMedium = Medium.grey(0.0, Point3.ZERO)
        val clear = Medium.grey(0.0, Point3.ONE)
        val outer = Fog(Point3.ZERO, cavities.toDouble() + 1, outerMedium, "outer")
        parts = listOf(outer) + List(cavities) { i ->
            Fog(Point3(i, 0, 0), 0.25, clear, "cavity-$i", outside = outerMedium)
        }
        world = World(parts)
    }

    @Setup(Level.Invocation)
    fun prepareEdit() {
        if (firstEdit) world = World(parts)
    }

    @Benchmark
    fun editGreen(): Medium? {
        alternate = !alternate
        world.editObject("outer", ObjectChange.Extinction(ObjectChange.Channel.G, if (alternate) 0.25 else 0.5))
        return (world.objects.last() as Fog).outside
    }
}
