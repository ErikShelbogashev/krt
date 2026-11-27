package krt.i9ns.objfile

import java.io.File
import kotlin.math.abs
import krt.domain.scene.model.Material
import krt.domain.scene.model.Triangle
import krt.platform.math.Point3

class ObjParser(filePath: String, material: Material) {
    private val vertices = mutableListOf<Point3>()
    private val normals = mutableListOf<Point3>()
    private val quads = mutableListOf<List<Int>>()
    val triangles: List<Triangle>

    init {
        parseFile(filePath)
        val normalized = normalizeVertices(vertices)
        triangles = quads.flatMap { quad ->
            // Two triangles from a quad
            listOf(
                Triangle(normalized[quad[0]], normalized[quad[1]], normalized[quad[2]], material),
                Triangle(normalized[quad[0]], normalized[quad[2]], normalized[quad[3]], material),
            )
        }
    }

    private fun parseFile(filePath: String) {
        File(filePath).forEachLine { line ->
            when {
                line.startsWith("v ") -> {
                    val parts = line.substring(2).split(" ").map { it.trim() }
                    vertices.add(Point3(parts[1].toDouble(), parts[2].toDouble(), parts[3].toDouble()))
                }

                line.startsWith("vn ") -> {
                    val parts = line.substring(2).split(" ").map { it.trim() }
                    normals.add(Point3(parts[1].toDouble(), parts[2].toDouble(), parts[3].toDouble()))
                }

                line.startsWith("f ") -> {
                    val parts = line.substring(2).split(" ").map { it.trim() }
                    if (parts.size == 5) { // Обработка четырехугольников
                        val idxGroups = parts.dropLast(1).map { it.split('/').map { it.toInt() - 1 } }
                        quads.add(idxGroups.map { it[0] })
                    }
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
}
