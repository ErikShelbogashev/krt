package krt.i9ns.pbrt

import krt.domain.scene.model.Fog
import krt.domain.scene.model.Fractal
import krt.domain.scene.model.Material
import krt.domain.scene.model.Medium
import krt.domain.scene.model.MediumInterface
import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Sphere
import krt.domain.scene.model.StartView
import krt.domain.scene.model.Traceable
import krt.domain.scene.model.Translucent
import krt.domain.scene.model.Triangle
import krt.i9ns.pbrt.PbrtText.p
import krt.i9ns.pbrt.PbrtText.v
import krt.platform.math.Point3

/** A scene file as written: the text of the main file and the files beside it, by the names it gives them. */
class WrittenScene(val text: String, val files: Map<String, ByteArray>)

/**
 * krt scenes as pbrt-v4 files that pbrt-v4 itself can render (ADR 0016): a pure transformation. What pbrt cannot
 * express is written as a `#krt` comment line before the pbrt directive that stands in for it.
 */
object PbrtWriter {

    /** Triangles of one mesh beyond which the mesh goes into a PLY file beside the scene. */
    private const val PLY_TRIANGLES = 512
    private const val HALF = 0.5
    private const val CORNERS = 3
    private val GENERATED = listOf("Sphere[", "Triangle[", "Fog[", "Translucent[", "Fractal[", "Mesh[")

    fun write(objects: List<Traceable>, look: SceneLook, start: StartView, name: String): WrittenScene {
        val media = objects.flatMap(::mediaOf).distinct()
        val names = media.withIndex().associate { (i, medium) -> medium to "medium_$i" }
        val files = linkedMapOf<String, ByteArray>()
        val text = buildString {
            append(PbrtLines.header(look, start, objects.any { it is Sphere && it.velocity != Point3.ZERO }))
            media.forEach { medium -> appendLine(PbrtLines.medium(names.getValue(medium), medium)) }
            appendLine()
            var i = 0
            while (i < objects.size) {
                val obj = objects[i]
                val group = if (obj is Triangle) meshGroup(objects, i) else emptyList()
                append(if (obj is Triangle) mesh(group, names, "$name-mesh$i.ply", files) else single(obj, names))
                i += maxOf(1, group.size)
            }
        }
        return WrittenScene(text, files)
    }

    /** The material lines of [material] (see [PbrtMaterialWriter]). */
    fun material(material: Material): List<String> = PbrtMaterialWriter.lines(material)

    private fun single(obj: Traceable, names: Map<Medium, String>): String {
        val lines = when (obj) {
            is Sphere -> listOfNotNull(PbrtLines.interfaceOf(obj.media, names)) + material(obj.material) +
                PbrtLines.at(obj.center) + PbrtLines.motion(obj.velocity) + PbrtLines.sphere(obj.radius)
            is Fog -> listOfNotNull(PbrtLines.interfaceOf(MediumInterface(obj.medium, obj.outside), names)) +
                "Material \"interface\"" + PbrtLines.at(obj.center) + PbrtLines.sphere(obj.radius)
            is Translucent -> listOfNotNull(PbrtLines.interfaceOf(MediumInterface(obj.medium, obj.outside), names)) +
                material(obj.surface.material) + PbrtLines.at(obj.center) + PbrtLines.sphere(obj.radius)
            is Fractal -> material(obj.material) + PbrtLines.at(obj.center) + PbrtLines.fractal(obj) +
                PbrtLines.sphere(obj.radius * HALF)
            else -> error("krt cannot write ${obj::class.simpleName}")
        }
        return attributes(obj.id, lines)
    }

    private fun attributes(id: String, lines: List<String>): String = buildString {
        appendLine("AttributeBegin")
        named(id)?.let { appendLine("  #krt Name \"$it\"") }
        lines.forEach { appendLine("  $it") }
        appendLine("AttributeEnd")
    }

    /** The triangles from [start] that go into one mesh: one object's, of one material and media. */
    private fun meshGroup(objects: List<Traceable>, start: Int): List<Triangle> {
        val first = objects[start] as Triangle
        val named = named(first.id) != null
        return objects.drop(start).takeWhile { obj ->
            obj is Triangle && obj.material === first.material && obj.media == first.media &&
                (obj.normals != null) == (first.normals != null) &&
                if (named) obj.id == first.id else named(obj.id) == null
        }.map { it as Triangle }
    }

    private fun mesh(
        group: List<Triangle>,
        names: Map<Medium, String>,
        file: String,
        files: MutableMap<String, ByteArray>,
    ): String {
        val first = group.first()
        val points = group.flatMap { listOf(it.v0, it.v1, it.v2) }
        val normals = group.mapNotNull { it.normals }.flatMap { listOf(it.n0, it.n1, it.n2) }.takeIf { it.isNotEmpty() }
        val shape = if (group.size > PLY_TRIANGLES) {
            val triangles = group.indices.map { intArrayOf(CORNERS * it, CORNERS * it + 1, CORNERS * it + 2) }
            files[file] = Ply.write(PlyMesh(points, normals, triangles))
            "Shape \"plymesh\" \"string filename\" \"$file\""
        } else {
            "Shape \"trianglemesh\" \"integer indices\" [${points.indices.joinToString(" ")}] " +
                "\"point3 P\" [${points.joinToString(" ", transform = ::v)}]" +
                (normals?.let { " \"normal N\" [${it.joinToString(" ", transform = ::v)}]" } ?: "")
        }
        val lines = listOfNotNull(PbrtLines.interfaceOf(first.media, names)) + material(first.material) + shape
        return attributes(first.id, lines)
    }

    private fun mediaOf(obj: Traceable): List<Medium> = when (obj) {
        is Sphere -> listOfNotNull(obj.media?.inside, obj.media?.outside)
        is Triangle -> listOfNotNull(obj.media?.inside, obj.media?.outside)
        is Fog -> listOfNotNull(obj.medium, obj.outside)
        is Translucent -> listOfNotNull(obj.medium, obj.outside)
        else -> emptyList()
    }

    /** The id of an object worth writing: one the scene gave it, not one krt made up. */
    private fun named(id: String): String? = id.takeUnless { GENERATED.any { prefix -> id.startsWith(prefix) } }
}

/** The lines of a pbrt-v4 file krt writes, one directive each: pure. */
object PbrtLines {
    private const val FILM_WIDTH = 800
    private const val FILM_HEIGHT = 600
    private const val PIXEL_SAMPLES = 64
    private const val MAX_DEPTH = 12
    private const val HALF = 0.5

    fun header(look: SceneLook, start: StartView, moving: Boolean): String = buildString {
        appendLine("# A krt scene in pbrt-v4's format; #krt lines are krt's own, pbrt reads them as comments.")
        appendLine("#krt Look \"bool sampleLights\" ${look.sampleLights} \"bool motionBlur\" ${look.motionBlur}")
        appendLine("# pbrt's camera space is left-handed, krt's right-handed: the mirror keeps the picture the same.")
        appendLine("Scale -1 1 1")
        appendLine("LookAt ${v(start.origin)}  ${v(start.target)}  0 1 0")
        val shutter = if (moving || look.motionBlur) " \"float shutteropen\" 0 \"float shutterclose\" 1" else ""
        appendLine(
            "Camera \"perspective\" ${p("float fov", look.fov)} ${p("float lensradius", look.aperture / 2)} " +
                p("float focaldistance", (start.target - start.origin).length()) + shutter,
        )
        appendLine("Film \"rgb\" \"integer xresolution\" $FILM_WIDTH \"integer yresolution\" $FILM_HEIGHT")
        appendLine("Sampler \"zsobol\" \"integer pixelsamples\" $PIXEL_SAMPLES")
        appendLine("Integrator \"volpath\" \"integer maxdepth\" $MAX_DEPTH")
        appendLine()
        appendLine("WorldBegin")
        appendLine("#krt Sky ${p("rgb horizon", look.sky.horizon)} ${p("rgb zenith", look.sky.zenith)}")
        appendLine("LightSource \"infinite\" ${p("rgb L", (look.sky.horizon + look.sky.zenith) * HALF)}")
    }

    fun medium(name: String, medium: Medium): String {
        val scattering = medium.density * medium.albedo
        val absorption = medium.density - scattering
        return "MakeNamedMedium \"$name\" \"string type\" \"homogeneous\" ${p("rgb sigma_a", absorption)} " +
            "${p("rgb sigma_s", scattering)} \"float scale\" 1"
    }

    fun at(center: Point3): String = "Translate ${v(center)}"

    fun sphere(radius: Double): String = "Shape \"sphere\" ${p("float radius", radius)}"

    fun motion(velocity: Point3): List<String> =
        if (velocity == Point3.ZERO) {
            emptyList()
        } else {
            listOf("ActiveTransform EndTime", "Translate ${v(velocity)}", "ActiveTransform All")
        }

    fun fractal(fractal: Fractal): String =
        "#krt Shape \"fractal\" \"string kind\" \"${fractal.kind.name.lowercase()}\" " +
            "${p("float radius", fractal.radius)} " +
            "\"integer iterations\" ${fractal.iterations} ${p("float parameter", fractal.parameter)} " +
            p("point3 seed", fractal.seed)

    fun interfaceOf(media: MediumInterface?, names: Map<Medium, String>): String? = media?.let {
        "MediumInterface \"${it.inside?.let(names::getValue) ?: ""}\" \"${it.outside?.let(names::getValue) ?: ""}\""
    }

}
