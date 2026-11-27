package krt.i9ns.pbrt

import java.nio.ByteBuffer
import java.nio.ByteOrder
import krt.platform.math.Point3

/** A triangle mesh as a PLY file holds it: [points], [normals] per point or none, and triangles of point indices. */
class PlyMesh(val points: List<Point3>, val normals: List<Point3>?, val triangles: List<IntArray>)

/**
 * PLY meshes, as pbrt-v4's `plymesh` takes them (ADR 0016): a pure transformation of bytes. Reads ASCII and binary
 * little- or big-endian files with x, y, z, optional nx, ny, nz and faces of any number of corners, cut into fans;
 * writes binary little-endian.
 */
object Ply {
    private const val AXES = 3

    private class Property(val name: String, val type: String, val countType: String? = null)

    private class Element(val name: String, val count: Int, val properties: List<Property>)

    fun read(bytes: ByteArray): PlyMesh {
        val headerEnd = findHeaderEnd(bytes)
        val header = String(bytes, 0, headerEnd, Charsets.US_ASCII).lines()
        require(header.firstOrNull()?.trim() == "ply") { "not a PLY file" }
        val format = header.first { it.startsWith("format") }.split(" ")[1]
        val elements = elements(header)
        val body = bytes.copyOfRange(headerEnd, bytes.size)
        val values = if (format == "ascii") AsciiValues(String(body, Charsets.US_ASCII)) else BinaryValues(body, format)
        val points = mutableListOf<Point3>()
        val normals = mutableListOf<Point3>()
        val triangles = mutableListOf<IntArray>()
        for (element in elements) {
            repeat(element.count) {
                val read = element.properties.associate { property -> property.name to values.read(property) }
                when (element.name) {
                    "vertex" -> {
                        points += Point3(read.number("x"), read.number("y"), read.number("z"))
                        if ("nx" in read) normals += Point3(read.number("nx"), read.number("ny"), read.number("nz"))
                    }
                    "face" -> {
                        @Suppress("UNCHECKED_CAST")
                        val corners = (read["vertex_indices"] ?: read["vertex_index"]) as List<Double>
                        for (k in 1 until corners.size - 1) {
                            triangles += intArrayOf(corners[0].toInt(), corners[k].toInt(), corners[k + 1].toInt())
                        }
                    }
                }
            }
        }
        return PlyMesh(points, normals.takeIf { it.size == points.size }, triangles)
    }

    fun write(mesh: PlyMesh): ByteArray {
        val hasNormals = mesh.normals != null
        val header = buildString {
            append("ply\nformat binary_little_endian 1.0\n")
            append("element vertex ${mesh.points.size}\n")
            append("property float x\nproperty float y\nproperty float z\n")
            if (hasNormals) append("property float nx\nproperty float ny\nproperty float nz\n")
            append("element face ${mesh.triangles.size}\n")
            append("property list uchar int vertex_indices\nend_header\n")
        }.toByteArray(Charsets.US_ASCII)
        val vertexBytes = mesh.points.size * Float.SIZE_BYTES * (if (hasNormals) 2 * AXES else AXES)
        val faceBytes = mesh.triangles.size * (1 + AXES * Int.SIZE_BYTES)
        val buffer = ByteBuffer.allocate(header.size + vertexBytes + faceBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(header)
        mesh.points.forEachIndexed { i, p ->
            buffer.putFloat(p.x.toFloat()).putFloat(p.y.toFloat()).putFloat(p.z.toFloat())
            mesh.normals?.get(i)?.let { n ->
                buffer.putFloat(n.x.toFloat()).putFloat(n.y.toFloat()).putFloat(n.z.toFloat())
            }
        }
        mesh.triangles.forEach { t -> buffer.put(AXES.toByte()).putInt(t[0]).putInt(t[1]).putInt(t[2]) }
        return buffer.array()
    }

    private fun Map<String, Any>.number(name: String): Double = this[name] as Double

    private fun findHeaderEnd(bytes: ByteArray): Int {
        val marker = "end_header".toByteArray(Charsets.US_ASCII)
        for (i in 0..bytes.size - marker.size) {
            if ((marker.indices).all { bytes[i + it] == marker[it] }) {
                var end = i + marker.size
                while (end < bytes.size && bytes[end] != '\n'.code.toByte()) end++
                return end + 1
            }
        }
        error("PLY header has no end_header")
    }

    private fun elements(header: List<String>): List<Element> {
        val elements = mutableListOf<Element>()
        var name = ""
        var count = 0
        var properties = mutableListOf<Property>()
        for (line in header.map { it.trim().split(Regex("\\s+")) }) {
            when (line[0]) {
                "element" -> {
                    if (name.isNotEmpty()) elements += Element(name, count, properties)
                    name = line[1]
                    count = line[2].toInt()
                    properties = mutableListOf()
                }
                "property" -> properties += if (line[1] == "list") {
                    Property(line.last(), line[line.size - 2], line[2])
                } else {
                    Property(line[2], line[1])
                }
            }
        }
        if (name.isNotEmpty()) elements += Element(name, count, properties)
        return elements
    }

    private interface Values {
        fun number(type: String): Double

        fun read(property: Property): Any = property.countType?.let { countType ->
            List(number(countType).toInt()) { number(property.type) }
        } ?: number(property.type)
    }

    private class AsciiValues(text: String) : Values {
        private val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.iterator()
        override fun number(type: String): Double = words.next().toDouble()
    }

    private class BinaryValues(bytes: ByteArray, format: String) : Values {
        private val buffer = ByteBuffer.wrap(bytes)
            .order(if (format == "binary_big_endian") ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)

        override fun number(type: String): Double = when (type) {
            "char", "int8" -> buffer.get().toDouble()
            "uchar", "uint8" -> java.lang.Byte.toUnsignedInt(buffer.get()).toDouble()
            "short", "int16" -> buffer.short.toDouble()
            "ushort", "uint16" -> java.lang.Short.toUnsignedInt(buffer.short).toDouble()
            "int", "int32" -> buffer.int.toDouble()
            "uint", "uint32" -> Integer.toUnsignedLong(buffer.int).toDouble()
            "float", "float32" -> buffer.float.toDouble()
            "double", "float64" -> buffer.double
            else -> error("PLY type $type")
        }
    }
}
