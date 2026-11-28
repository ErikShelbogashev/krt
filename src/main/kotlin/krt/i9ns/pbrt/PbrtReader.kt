package krt.i9ns.pbrt

import kotlin.math.PI
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Material
import krt.domain.scene.model.Medium
import krt.domain.scene.model.MediumInterface
import krt.domain.scene.model.Pattern
import krt.domain.scene.model.SceneDescription
import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Sky
import krt.domain.scene.model.Sphere
import krt.domain.scene.model.StartView
import krt.domain.scene.model.Traceable
import krt.platform.math.Color
import krt.platform.math.Matrix4
import krt.platform.math.Point3

/**
 * A pbrt-v4 scene file as krt draws it (ADR 0016): a pure transformation of the file's text and the files it
 * refers to ([references]: `Include`, `Import`, `plymesh`), which the caller reads. What krt cannot draw as
 * written gets its nearest krt counterpart and a warning in the result.
 */
object PbrtReader {

    /** The files [text] refers to, by the names it gives: included scene files and PLY meshes. */
    fun references(text: String): List<String> = PbrtSyntax.parse(text).mapNotNull { d ->
        when {
            d.name == "Include" || d.name == "Import" -> d.string(0)
            d.name == "Shape" && d.string(0) == "plymesh" -> d.text("filename", "")
            else -> null
        }
    }.filter { it.isNotEmpty() }.distinct()

    /** The scene of [text]; [files] holds the referred files by name, their bytes as read. */
    fun read(text: String, files: Map<String, ByteArray> = emptyMap()): SceneDescription {
        val state = ReadState(files)
        state.run(PbrtSyntax.parse(text))
        return state.description()
    }
}

/** The graphics state of pbrt: transforms at the start and end of the shutter, material, light, media. */
private class Graphics(
    var placement: Placement = Placement(),
    var material: MaterialSpec = MaterialSpec(Lambert(Color(DEFAULT_GREY, DEFAULT_GREY, DEFAULT_GREY))),
    var light: Material? = null,
    var inside: Medium? = null,
    var outside: Medium? = null,
) {
    fun copy() = Graphics(placement, material, light, inside, outside)

    /** Concatenates [matrix] to the transforms [active] for: StartTime, EndTime or All. */
    fun concat(matrix: Matrix4, active: String, motion: Point3 = Point3.ZERO) {
        val p = placement
        placement = p.copy(
            ctm = if (active != ACTIVE_END) p.ctm * matrix else p.ctm,
            ctmEnd = if (active != ACTIVE_START) p.ctmEnd * matrix else p.ctmEnd,
            motion = p.motion + if (active == ACTIVE_END) p.ctm.vector(motion) else Point3.ZERO,
        )
    }

    fun replace(matrix: Matrix4, active: String) {
        val p = placement
        placement = p.copy(
            ctm = if (active != ACTIVE_END) matrix else p.ctm,
            ctmEnd = if (active != ACTIVE_START) matrix else p.ctmEnd,
        )
    }

    fun covering(name: String?): Covering {
        val inside = material.medium ?: inside
        val media = if (inside != null || outside != null) MediumInterface(inside, outside) else null
        return Covering(light ?: material.material, media, name)
    }

    companion object {
        private const val DEFAULT_GREY = 0.5
    }
}

private const val ACTIVE_ALL = "All"
private const val ACTIVE_START = "StartTime"
private const val ACTIVE_END = "EndTime"

/** The reading of one file: the local mutable state of the transformation. */
private class ReadState(val files: Map<String, ByteArray>) {
    val objects = mutableListOf<Traceable>()
    val warnings = mutableListOf<String>()
    val textures = mutableMapOf<String, Pattern>()
    val materials = mutableMapOf<String, MaterialSpec>()
    val media = mutableMapOf<String, Medium>()
    var graphics = Graphics()
    val stack = ArrayDeque<Graphics>()
    var active = ACTIVE_ALL
    var camera = CameraSpec()
    var sky: Sky? = null
    var sampleLights: Boolean? = null
    var motionBlur: Boolean? = null
    val pending = mutableMapOf<String, PbrtDirective>()
    var pendingName: String? = null
    var meshes = 0

    fun description(): SceneDescription {
        val moving = objects.any { it is Sphere && it.velocity != Point3.ZERO }
        val look = SceneLook(
            sky ?: Sky(Color.ZERO, Color.ZERO), camera.fov, camera.lensRadius * 2, sampleLights ?: true,
            motionBlur ?: moving, camera.start,
        )
        return SceneDescription(objects.toList(), look, warnings.toList())
    }

    fun run(directives: List<PbrtDirective>) {
        for (d in directives) {
            if (d.krt) krt(d) else directive(d)
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun directive(d: PbrtDirective) {
        when (d.name) {
            "WorldBegin" -> graphics.placement = Placement()
            "LookAt", "Camera" -> camera = camera.with(d, warnings)
            in IGNORED -> Unit
            "AttributeBegin", "TransformBegin" -> stack.addLast(graphics.copy())
            "AttributeEnd", "TransformEnd" -> graphics = stack.removeLastOrNull() ?: d.fail("unbalanced end")
            "Identity", "Transform" -> graphics.replace(PbrtTransforms.of(d), active)
            "Translate" -> graphics.concat(PbrtTransforms.of(d), active, PbrtTransforms.translation(d))
            "Scale", "Rotate", "ConcatTransform" -> graphics.concat(PbrtTransforms.of(d), active)
            "ActiveTransform" -> active = d.args.firstOrNull()?.toString() ?: ACTIVE_ALL
            "ReverseOrientation" -> graphics.placement = graphics.placement.let { it.copy(reverse = !it.reverse) }
            "Material", "MakeNamedMaterial", "NamedMaterial" -> material(d)
            "Texture" -> PbrtTextures.of(d, textures, warnings)?.let { textures[d.string(0)] = it }
            "MakeNamedMedium" -> media[d.string(0)] = PbrtMaterials.medium(d, warnings)
            "MediumInterface" -> mediumInterface(d)
            "AreaLightSource" -> graphics.light = pending.remove("AreaLightSource")?.let(KrtExtensions::light)
                ?: PbrtMaterials.areaLight(d, warnings)
            "LightSource" -> lightSource(d)
            "Shape" -> shape(d)
            "Include", "Import" -> run(PbrtSyntax.parse(String(files[d.string(0)] ?: d.fail("file not given"))))
            else -> warnings += "line ${d.line}: directive ${d.name} not supported"
        }
    }

    private fun krt(d: PbrtDirective) {
        when (d.name) {
            "Material", "AreaLightSource", "Shape" -> pending[d.name] = d
            "Name" -> pendingName = d.string(0)
            "Sky" -> sky = Sky(PbrtValues.color(d, "horizon", Point3.ONE), PbrtValues.color(d, "zenith", Point3.ONE))
            "Look" -> {
                if (d.params.containsKey("sampleLights")) sampleLights = d.bool("sampleLights", true)
                if (d.params.containsKey("motionBlur")) motionBlur = d.bool("motionBlur", false)
            }
            else -> warnings += "line ${d.line}: krt directive ${d.name} unknown"
        }
    }

    private fun material(d: PbrtDirective) {
        if (d.name == "NamedMaterial") {
            graphics.material = materials[d.string(0)] ?: d.fail("no material ${d.string(0)}")
            return
        }
        val type = if (d.name == "Material") d.string(0) else d.text("type", "diffuse")
        val spec = pending.remove("Material")?.let(KrtExtensions::material)
            ?: PbrtMaterials.of(type, d, textures, warnings)
        if (d.name == "Material") graphics.material = spec else materials[d.string(0)] = spec
    }

    private fun mediumInterface(d: PbrtDirective) {
        fun named(index: Int): Medium? = (d.args.getOrNull(index) as? String)?.takeIf { it.isNotEmpty() }?.let {
            media[it] ?: d.fail("no medium $it")
        }
        graphics.inside = named(0)
        graphics.outside = if (d.args.size > 1) named(1) else graphics.inside
    }

    private fun lightSource(d: PbrtDirective) {
        when (d.string(0)) {
            "infinite" -> if (sky == null) {
                val l = PbrtValues.color(d, "L", Point3.ONE) * d.double("scale", 1.0)
                sky = Sky(l, l)
            }
            "point", "spot" -> {
                val from = graphics.placement.ctm.point(PbrtValues.vector(d, "from") ?: Point3.ZERO)
                val intensity = PbrtValues.color(d, "I", Point3.ONE) * d.double("scale", 1.0)
                val radiance = intensity / (PI * POINT_RADIUS * POINT_RADIUS)
                objects += Sphere(from, POINT_RADIUS, Emissive(radiance, 1.0), "Light[${objects.size}]")
                warnings += "line ${d.line}: ${d.string(0)} light drawn as a small glowing ball"
            }
            else -> warnings += "line ${d.line}: light ${d.string(0)} not supported"
        }
    }

    private fun shape(d: PbrtDirective) {
        val covering = graphics.covering(pendingName)
        pendingName = null
        val placement = graphics.placement
        val krtShape = pending.remove("Shape")
        val shape = if (krtShape != null) "krt" else d.string(0)
        objects += when (shape) {
            "krt" -> listOf(PbrtGeometry.fractal(krtShape!!, placement, covering, objects.size, warnings))
            "sphere" -> listOf(PbrtGeometry.sphere(d, placement, covering, objects.size))
            "plymesh" -> mesh(d, Ply.read(files[d.text("filename", "")] ?: d.fail("PLY file not given")), covering)
            "trianglemesh", "loopsubdiv" -> mesh(d, PbrtGeometry.meshOf(d), covering)
            "bilinearmesh" -> mesh(d, PbrtGeometry.bilinear(d), covering)
            "disk" -> mesh(d, PbrtShapes.disk(d.double("radius", 1.0), d.double("height", 0.0)), covering)
            "cylinder" -> mesh(d, PbrtShapes.cylinder(d), covering)
            else -> {
                warnings += "line ${d.line}: shape $shape not supported"
                emptyList()
            }
        }
        if (shape == "loopsubdiv") warnings += "line ${d.line}: loopsubdiv drawn without subdivision"
    }

    private fun mesh(d: PbrtDirective, mesh: PlyMesh, covering: Covering): List<Traceable> =
        PbrtGeometry.mesh(d, mesh, graphics.placement, covering.copy(name = covering.name ?: "Mesh[${meshes++}]"))

    private companion object {
        val IGNORED = setOf(
            "Film", "Sampler", "Integrator", "PixelFilter", "ColorSpace", "Option", "Accelerator", "Attribute",
            "TransformTimes", "CoordinateSystem", "CoordSysTransform", "CameraMedium",
        )
        const val POINT_RADIUS = 0.05
    }
}

/** The camera of a scene file: the view LookAt gives and the lens Camera gives. */
private data class CameraSpec(
    val start: StartView? = null,
    val fov: Double = DEFAULT_FOV,
    val lensRadius: Double = 0.0,
) {
    fun with(d: PbrtDirective, warnings: MutableList<String>): CameraSpec =
        if (d.name == "LookAt") {
            val n = d.numbers()
            if (n.size < LOOK_AT_VALUES) d.fail("9 numbers expected")
            copy(start = StartView(Point3(n[0], n[1], n[2]), Point3(n[TARGET], n[TARGET + 1], n[TARGET + 2])))
        } else {
            if (d.string(0) != "perspective") warnings += "line ${d.line}: camera ${d.string(0)} drawn as perspective"
            copy(fov = d.double("fov", DEFAULT_FOV), lensRadius = d.double("lensradius", 0.0))
        }

    private companion object {
        const val DEFAULT_FOV = 90.0
        const val LOOK_AT_VALUES = 9
        const val TARGET = 3
    }
}

/** pbrt transform directives as matrices: a pure transformation. */
private object PbrtTransforms {
    fun of(d: PbrtDirective): Matrix4 {
        val n = d.numbers()
        return when (d.name) {
            "Translate" -> Matrix4.translate(Point3(n[0], n[1], n[2]))
            "Scale" -> Matrix4.scale(Point3(n[0], n[1], n[2]))
            "Rotate" -> Matrix4.rotate(n[0], Point3(n[1], n[2], n[AXIS_Z]))
            "Transform", "ConcatTransform" -> Matrix4.ofColumns(n)
            else -> Matrix4.IDENTITY
        }
    }

    fun translation(d: PbrtDirective): Point3 = d.numbers().let { Point3(it[0], it[1], it[2]) }

    private const val AXIS_Z = 3
}
