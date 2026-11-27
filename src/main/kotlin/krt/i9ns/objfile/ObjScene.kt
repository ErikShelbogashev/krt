package krt.i9ns.objfile

import kotlin.math.atan
import kotlin.math.pow
import krt.domain.scene.model.Dielectric
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.Fog
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Material
import krt.domain.scene.model.Medium
import krt.domain.scene.model.MediumInterface
import krt.domain.scene.model.SceneDescription
import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Sky
import krt.domain.scene.model.StartView
import krt.domain.scene.model.Traceable
import krt.domain.scene.model.Triangle
import krt.domain.scene.model.VertexNormals
import krt.platform.math.Color
import krt.platform.math.Matrix4
import krt.platform.math.Point3

/** The files of an OBJ scene: the OBJ, its MTL, and the optional sidecar and layout of a krt showcase. */
class ObjFiles(val obj: String, val mtl: String? = null, val showcase: String? = null, val layout: String? = null)

/** An OBJ scene that does not hold together: a binding that matches nothing, a material that does not exist. */
class ObjSceneError(message: String) : IllegalArgumentException(message)

/**
 * An OBJ scene as krt draws it (ADR 0016): a pure transformation. Objects keep their names; each takes the
 * material of the last `render-showcase.json` binding that matches it, else its MTL colour. A Z-up scene (its
 * layout says so) turns to krt's Y-up once: (x, y, z) to (x, z, −y), points, normals, camera and volumes alike.
 * Nothing is scaled: the scene keeps its own units.
 */
object ObjScene {

    private const val SENSOR_MM = 36.0
    private const val ASPECT = 16.0 / 9.0
    private const val DEGREES = 180.0 / Math.PI
    private const val GAMMA = 2.2
    private const val HEX = 16
    private const val DEFAULT_FOV = 40.0

    /** The MTL file OBJ text [obj] named [name] uses: its `mtllib`, or else the file of its own name. */
    fun materialLibrary(obj: String, name: String): String =
        MTLLIB.find(obj)?.groupValues?.get(1)?.trim() ?: (name.substringBeforeLast('.') + ".mtl")

    private val MTLLIB = Regex("(?m)^mtllib\\s+(.+)$")

    fun read(files: ObjFiles): SceneDescription {
        val model = ObjText.obj(files.obj)
        val mtl = files.mtl?.let(ObjText::mtl).orEmpty()
        val sidecar = files.showcase?.let(Sidecar::of)
        val layout = files.layout?.let(SceneLayout::of)
        val up = if (layout?.zUp == true) Z_UP else Matrix4.IDENTITY
        val names = model.objects.map { it.name }
        sidecar?.let { validate(it, names) }
        val bound = sidecar?.let { bindings(it, names) }.orEmpty()
        val volumes = sidecar?.volumes.orEmpty().map { volume ->
            Fog(up.point(volume.center), volume.radius, Medium.grey(volume.density, volume.albedo), volume.name)
        }
        val materials = SidecarMaterials(sidecar?.materials.orEmpty(), mtl, layout?.rotations.orEmpty(), up)
        val objects = model.objects.flatMap { obj ->
            val spec = materials.of(obj, bound[obj.name])
            triangles(model, obj, spec.material, mediaOf(spec.medium, volumes, model, obj, up), up)
        }
        val look = SceneLook(
            sky(layout?.worldColor), fov(layout?.focalLength), 0.0, sidecar?.sampleLights ?: true, false,
            layout?.cameraPosition?.let { position ->
                StartView(up.point(position), up.point(layout.cameraTarget ?: Point3.ZERO))
            },
        )
        return SceneDescription(volumes + objects, look)
    }

    /** Every glob of the sidecar matches an object and every bound material exists, or the scene is in error. */
    fun validate(sidecar: Sidecar, names: List<String>) {
        val duplicates = names.groupBy { it }.filter { it.value.size > 1 }.keys
        val globs = sidecar.bindings.flatMap { it.globs } + sidecar.lightGlobs
        val unmatched = globs.filter { glob -> names.none { Glob.matches(glob, it) } }
        val missing = sidecar.bindings.map { it.material }.filter { it !in sidecar.materials }
        val problems = listOfNotNull(
            duplicates.takeIf { it.isNotEmpty() }?.let { "object names used twice: $it" },
            unmatched.takeIf { it.isNotEmpty() }?.let { "globs that match no object: $it" },
            missing.takeIf { it.isNotEmpty() }?.let { "materials bound but not defined: $it" },
        )
        if (problems.isNotEmpty()) throw ObjSceneError(problems.joinToString("; "))
    }

    /** The material each object takes: the last binding that matches it wins. */
    private fun bindings(sidecar: Sidecar, names: List<String>): Map<String, SidecarMaterial> =
        names.mapNotNull { name ->
            sidecar.bindings.lastOrNull { binding -> binding.globs.any { Glob.matches(it, name) } }
                ?.let { name to sidecar.materials.getValue(it.material) }
        }.toMap()

    /** The media of an object: its own inside, and outside the fog volume around it, if any. */
    @Suppress("LongParameterList")
    private fun mediaOf(
        inside: Medium?,
        volumes: List<Fog>,
        model: ObjModel,
        obj: ObjObject,
        up: Matrix4,
    ): MediumInterface? {
        inside ?: return null
        val points = obj.faces.flatMap { face -> face.points.map { up.point(model.points[it]) } }
        val center = points.fold(Point3.ZERO) { sum, p -> sum + p } / points.size.toDouble()
        return MediumInterface(inside, volumes.firstOrNull { it.contains(center) }?.medium)
    }

    @Suppress("LongParameterList")
    private fun triangles(
        model: ObjModel,
        obj: ObjObject,
        material: Material,
        media: MediumInterface?,
        up: Matrix4,
    ): List<Triangle> =
        obj.faces.map { face ->
            val (a, b, c) = face.points.map { up.point(model.points[it]) }
            val normals = face.normals.takeIf { corners -> corners.all { it >= 0 } && corners.toSet().size > 1 }
                ?.map { up.normal(model.normals[it]) }
            Triangle(a, b, c, material, obj.name, normals?.let { (n0, n1, n2) -> VertexNormals(n0, n1, n2) }, media)
        }

    /** The world colour of the layout, an sRGB hex code, as a uniform sky in linear light. */
    private fun sky(hex: String?): Sky {
        val code = hex?.removePrefix("#")?.toIntOrNull(HEX) ?: return Sky(Color.ZERO, Color.ZERO)
        val channel = { shift: Int -> ((code shr shift) and BYTE).toDouble() / BYTE }
        val color = Color(channel(RED).pow(GAMMA), channel(GREEN).pow(GAMMA), channel(0).pow(GAMMA))
        return Sky(color, color)
    }

    /** The vertical field of view of a lens of [focalLength] mm on a 36 mm wide sensor, for a 16:9 frame. */
    private fun fov(focalLength: Double?): Double =
        focalLength?.let { 2 * atan(SENSOR_MM / 2 / it / ASPECT) * DEGREES } ?: DEFAULT_FOV

    /** Z-up to Y-up: (x, y, z) to (x, z, −y), a rotation, so sides and normals keep their sense. */
    private val Z_UP = Matrix4.rotate(-90.0, Point3(1.0, 0.0, 0.0))

    private const val BYTE = 255
    private const val RED = 16
    private const val GREEN = 8
}

/** Globs of object names: `*` for any run of characters, everything else literal. */
object Glob {
    fun matches(glob: String, name: String): Boolean =
        Regex(glob.split("*").joinToString(".*") { Regex.escape(it) }).matches(name)
}
