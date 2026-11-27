package krt.i9ns.objfile

import java.io.File
import kotlin.math.abs
import krt.domain.scene.model.Material
import krt.domain.scene.model.Triangle
import krt.platform.math.Point3

class ObjParser(filePath: String, material: Material) {
    private val vertices = mutableListOf<Point3>()
    private val normals = mutableListOf<Point3>()
    private val faces = mutableListOf<List<Int>>()
    val triangles: List<Triangle>

    init {
        parseFile(filePath)
        val normalized = normalizeVertices(vertices)
        triangles = faces.flatMap { face ->
            // Fan triangulation: a triangle stays as is, a quad gives two triangles, and so on.
            (1 until face.size - 1).map { i ->
                Triangle(normalized[face[0]], normalized[face[i]], normalized[face[i + 1]], material)
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
                    val vertexIndices = line.fields().drop(1).map { it.substringBefore('/').toInt() - 1 }
                    if (vertexIndices.size >= MIN_FACE_VERTICES) faces.add(vertexIndices)
                }
            }
        }
    }

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
