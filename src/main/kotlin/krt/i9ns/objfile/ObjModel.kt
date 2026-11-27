package krt.i9ns.objfile

import krt.platform.math.Point3

/** One named object of an OBJ file: its triangles as corners into the file's points and normals, and its MTL name. */
class ObjObject(val name: String, val material: String?, val faces: List<ObjFace>)

/** A triangle of an OBJ file: three point indices and, per corner, a normal index or -1 (0-based, resolved). */
class ObjFace(val points: IntArray, val normals: IntArray)

/** An OBJ file as read: [points], [normals] and [objects] in file order; [mtlLibraries] it names. */
class ObjModel(
    val points: List<Point3>,
    val normals: List<Point3>,
    val objects: List<ObjObject>,
    val mtlLibraries: List<String>,
)

/** A material of an MTL file: diffuse colour, emission, transparency and refraction index. */
data class MtlMaterial(val diffuse: Point3, val emission: Point3, val dissolve: Double, val ior: Double)

/**
 * Wavefront OBJ and MTL text (ADR 0016): pure transformations. Keeps `o` (or `g`) names and `usemtl` per face,
 * reads `v`, `vn` and faces `v`, `v/vt`, `v//vn`, `v/vt/vn` with 1-based or negative indices, cut into fans that
 * keep each corner's normal.
 */
object ObjText {

    private const val DEFAULT_IOR = 1.5

    fun obj(text: String): ObjModel {
        val points = mutableListOf<Point3>()
        val normals = mutableListOf<Point3>()
        val libraries = mutableListOf<String>()
        val objects = mutableListOf<ObjObject>()
        var name = "object"
        var material: String? = null
        var faces = mutableListOf<ObjFace>()
        fun close() {
            if (faces.isNotEmpty()) objects += ObjObject(name, material, faces)
            faces = mutableListOf()
        }
        text.lineSequence().forEachIndexed { index, raw ->
            val fields = raw.substringBefore('#').trim().split(WHITESPACE).filter { it.isNotEmpty() }
            when (fields.firstOrNull()) {
                "v" -> points += point(fields)
                "vn" -> normals += point(fields).unit()
                "mtllib" -> libraries += fields.drop(1).joinToString(" ")
                "o", "g" -> { close(); name = fields.drop(1).joinToString(" ").ifEmpty { name } }
                "usemtl" -> { close(); material = fields.getOrNull(1) }
                "f" -> faces += face(fields.drop(1), points.size, normals.size, index + 1)
            }
        }
        close()
        return ObjModel(points, normals, objects, libraries)
    }

    fun mtl(text: String): Map<String, MtlMaterial> {
        val materials = linkedMapOf<String, MtlMaterial>()
        var name: String? = null
        var current = NEW_MATERIAL
        fun close() = name?.let { materials[it] = current }
        text.lineSequence().forEach { raw ->
            val fields = raw.substringBefore('#').trim().split(WHITESPACE).filter { it.isNotEmpty() }
            when (fields.firstOrNull()) {
                "newmtl" -> {
                    close()
                    name = fields.getOrNull(1)
                    current = NEW_MATERIAL
                }
                "Kd" -> current = current.copy(diffuse = point(fields))
                "Ke" -> current = current.copy(emission = point(fields))
                "d" -> current = current.copy(dissolve = fields[1].toDouble())
                "Tr" -> current = current.copy(dissolve = 1 - fields[1].toDouble())
                "Ni" -> current = current.copy(ior = fields[1].toDouble())
            }
        }
        close()
        return materials
    }

    private fun point(fields: List<String>): Point3 =
        Point3(fields[1].toDouble(), fields[2].toDouble(), fields.getOrNull(Z_FIELD)?.toDouble() ?: 0.0)

    /** The triangles of a face line: a fan from its first corner. */
    private fun face(corners: List<String>, points: Int, normals: Int, line: Int): List<ObjFace> {
        require(corners.size >= TRIANGLE) { "line $line: a face needs three corners" }
        val p = corners.map { index(it.substringBefore('/'), points, line) }
        val n = corners.map { corner ->
            corner.split('/').getOrNull(2)?.takeIf { it.isNotEmpty() }?.let { index(it, normals, line) } ?: -1
        }
        return (1 until corners.size - 1).map { i ->
            ObjFace(intArrayOf(p[0], p[i], p[i + 1]), intArrayOf(n[0], n[i], n[i + 1]))
        }
    }

    /** A 1-based or negative OBJ index as a 0-based one into [count] items read so far. */
    private fun index(text: String, count: Int, line: Int): Int {
        val value = text.toInt()
        val index = if (value < 0) count + value else value - 1
        require(index in 0 until count) { "line $line: index $value out of range" }
        return index
    }

    private val WHITESPACE = Regex("\\s+")
    private const val DEFAULT_GREY = 0.5
    private val NEW_MATERIAL =
        MtlMaterial(Point3(DEFAULT_GREY, DEFAULT_GREY, DEFAULT_GREY), Point3.ZERO, 1.0, DEFAULT_IOR)
    private const val Z_FIELD = 3
    private const val TRIANGLE = 3
}
