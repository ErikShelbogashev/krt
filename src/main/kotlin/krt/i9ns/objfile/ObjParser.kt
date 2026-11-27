package krt.i9ns.objfile

import java.io.File
import kotlin.math.abs
import krt.domain.scene.model.Material
import krt.domain.scene.model.Triangle
import krt.domain.scene.model.VertexNormals
import krt.platform.math.Point3

class ObjParser(filePath: String, material: Material) {
    private val vertices = mutableListOf<Point3>()
    private val normals = mutableListOf<Point3>()
    private val faces = mutableListOf<List<Int>>()

    /** Normal indices of each face's corners, or null for a face without them: such a face stays flat. */
    private val faceNormals = mutableListOf<List<Int>?>()
    val triangles: List<Triangle>

    init {
        parseFile(filePath)
        val normalized = normalizeVertices(vertices)
        triangles = faces.flatMapIndexed { index, face ->
            val corners = faceNormals[index]
            // Fan triangulation: a triangle stays as is, a quad gives two triangles, and so on.
            (1 until face.size - 1).map { i ->
                val smooth = corners?.let { VertexNormals(normal(it[0]), normal(it[i]), normal(it[i + 1])) }
                Triangle(normalized[face[0]], normalized[face[i]], normalized[face[i + 1]], material, normals = smooth)
            }
        }
    }

    private fun parseFile(filePath: String) {
        File(filePath).forEachLine { line ->
            when {
                line.startsWith("v ") -> {
                    val parts = line.fields()
                    vertices.add(Point3(parts[1].toDouble(), parts[2].toDouble(), parts[3].toDouble()))
                }

                line.startsWith("vn ") -> {
                    val parts = line.fields()
                    normals.add(Point3(parts[1].toDouble(), parts[2].toDouble(), parts[3].toDouble()))
                }

                line.startsWith("f ") -> {
                    val corners = line.fields().drop(1)
                    val vertexIndices = corners.map { it.substringBefore('/').toInt() - 1 }
                    val normalIndices = corners.map { it.split('/').getOrNull(2)?.toIntOrNull()?.minus(1) }
                    if (vertexIndices.size >= MIN_FACE_VERTICES) {
                        faces.add(vertexIndices)
                        // A face whose corners all share one normal is flat: it needs none.
                        val smooth = normalIndices.takeIf { all -> all.all { it != null } && all.toSet().size > 1 }
                        faceNormals.add(smooth?.map { it!! })
                    }
                }
            }
        }
    }

    private fun normal(index: Int): Point3 = normals[index].unit()

    private fun normalizeVertices(vertices: List<Point3>): List<Point3> {
        if (vertices.isEmpty()) return vertices
        val maxCoordinate = vertices.fold(0.0) { acc, vertex ->
            maxOf(acc, abs(vertex.x), abs(vertex.y), abs(vertex.z))
        }
        if (maxCoordinate == 0.0) return vertices // To avoid division by zero if all vertices are at the origin.
        return vertices.map { it / maxCoordinate }
    }

    private companion object {
        const val MIN_FACE_VERTICES = 3

        /** Splits an OBJ line into fields separated by any run of whitespace. */
        fun String.fields(): List<String> = trim().split(WHITESPACE)

        val WHITESPACE = Regex("\\s+")
    }
}
