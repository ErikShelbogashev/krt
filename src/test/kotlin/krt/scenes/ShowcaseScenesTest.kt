package krt.scenes

import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import krt.domain.scene.model.Fog
import krt.domain.scene.model.Fractal
import krt.domain.scene.model.Triangle
import krt.domain.trace.Emitters
import krt.i9ns.pbrt.PbrtWriter
import krt.i9ns.pbrt.Ply
import krt.platform.math.Point3

/**
 * The showcase scene files are what [ShowcaseScenes] builds. After changing a builder, write the files again with
 * `./gradlew test --tests '*ShowcaseScenesTest*' -Dkrt.writeScenes=true`.
 */
class ShowcaseScenesTest {

    private val directory = File("src/main/resources/krt/scenes")

    @Test
    fun `each showcase scene file is the one its builder writes`() {
        val write = System.getProperty("krt.writeScenes") == "true"
        ShowcaseScenes.ALL.forEach { scene ->
            val start = requireNotNull(scene.look.start)
            val written = PbrtWriter.write(scene.objects, scene.look, start, scene.file)
            val files = written.files + ("${scene.file}.pbrt" to written.text.toByteArray())
            files.forEach { (name, bytes) ->
                val file = File(directory, name)
                if (write) file.writeBytes(bytes)
                assertTrue(same(name, bytes, file.readBytes()), "$name is out of date: write the scenes again")
            }
        }
    }

    @Test
    fun `each showcase scene keeps within the load of the showcase`() {
        ShowcaseScenes.ALL.forEach { scene ->
            val triangles = scene.objects.count { it is Triangle }
            val lights = Emitters.of(scene.objects).size
            assertTrue(triangles < MAX_TRIANGLES, "${scene.file}: $triangles triangles")
            assertTrue(lights in 1..MAX_LIGHTS, "${scene.file}: $lights lights")
            assertTrue(scene.objects.count { it is Fractal } <= MAX_FRACTALS, "${scene.file}: too many fractals")
            val hazes = scene.objects.filterIsInstance<Fog>()
            assertTrue(hazes.all { it.radius <= MAX_FOG_RADIUS }, "${scene.file}: haze too wide")
            assertEquals(true, scene.look.sampleLights, "${scene.file} samples its lights")
        }
    }

    /**
     * The same scene file up to the last bits of its numbers: sines and cosines may round differently on another
     * machine, so numbers count as equal within [EPSILON], and the points of a PLY file within [FLOAT_EPSILON].
     */
    private fun same(name: String, written: ByteArray, stored: ByteArray): Boolean = if (name.endsWith(".ply")) {
        val (a, b) = Ply.read(written) to Ply.read(stored)
        a.triangles.map { it.toList() } == b.triangles.map { it.toList() } &&
            close(a.points, b.points) && close(a.normals.orEmpty(), b.normals.orEmpty())
    } else {
        val (a, b) = String(written) to String(stored)
        val numbers = NUMBER.findAll(a).zip(NUMBER.findAll(b))
        a.replace(NUMBER, "#") == b.replace(NUMBER, "#") &&
            numbers.all { (x, y) -> abs(x.value.toDouble() - y.value.toDouble()) < EPSILON }
    }

    private fun close(a: List<Point3>, b: List<Point3>) =
        a.size == b.size && a.zip(b).all { (p, q) -> (p - q).length() < FLOAT_EPSILON }

    private companion object {
        const val MAX_TRIANGLES = 50_000
        const val MAX_LIGHTS = 6
        const val MAX_FRACTALS = 2

        /** Haze stays in a small closed volume around what it shows, not through the whole scene. */
        const val MAX_FOG_RADIUS = 4.5

        val NUMBER = Regex("""-?\d+\.\d+(E-?\d+)?""")
        const val EPSILON = 1e-9
        const val FLOAT_EPSILON = 1e-5
    }
}
