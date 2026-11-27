package krt.i9ns.objfile

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import krt.domain.scene.model.Dielectric
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.LineSegment
import krt.platform.math.Color
import krt.platform.math.Point3

class ObjParserTest {

    private fun objFile(content: String): File =
        File.createTempFile("krt", ".obj").apply {
            deleteOnExit()
            writeText(content)
        }

    @Test
    fun `quad face is split into two triangles and vertices are normalized`() {
        val file = objFile("v  0 0 0\nv  4 0 0\nv  4 2 0\nv  0 2 0\nf 1 2 3 4 \n")
        val triangles = ObjParser(file.absolutePath, Lambert(Color(0.5, 0.5, 0.5))).triangles
        assertEquals(2, triangles.size)
        val segments = triangles.flatMap { it.linearize(1.0) }
        assertEquals(
            listOf(
                LineSegment(Point3(0.0, 0.0, 0.0), Point3(1.0, 0.0, 0.0)),
                LineSegment(Point3(1.0, 0.0, 0.0), Point3(1.0, 0.5, 0.0)),
                LineSegment(Point3(1.0, 0.5, 0.0), Point3(0.0, 0.0, 0.0)),
                LineSegment(Point3(0.0, 0.0, 0.0), Point3(1.0, 0.5, 0.0)),
                LineSegment(Point3(1.0, 0.5, 0.0), Point3(0.0, 0.5, 0.0)),
                LineSegment(Point3(0.0, 0.5, 0.0), Point3(0.0, 0.0, 0.0)),
            ),
            segments
        )
    }

    @Test
    fun `triangle faces and quads without trailing space are read`() {
        val file = objFile("v  0 0 0\nv  1 0 0\nv  0 1 0\nv  1 1 0\nf 1 2 3 \nf 1 2 4 3\n")
        assertEquals(3, ObjParser(file.absolutePath, Lambert(Color(0.5, 0.5, 0.5))).triangles.size)
    }

    @Test
    fun `standard single-space format with texture and normal indices is read`() {
        val file = objFile("v 0 0 0\nv 2 0 0\nv 0 2 0\nvn 0 0 1\nf 1/1/1 2/2/1 3/3/1\n")
        val triangle = ObjParser(file.absolutePath, Lambert(Color(0.5, 0.5, 0.5))).triangles.single()
        assertEquals(
            listOf(
                LineSegment(Point3(0.0, 0.0, 0.0), Point3(1.0, 0.0, 0.0)),
                LineSegment(Point3(1.0, 0.0, 0.0), Point3(0.0, 1.0, 0.0)),
                LineSegment(Point3(0.0, 1.0, 0.0), Point3(0.0, 0.0, 0.0)),
            ),
            triangle.linearize(1.0)
        )
    }

    @Test
    fun `bundled sample model parses`() {
        val triangles = ObjParser(File("samples/ORIGAMI_Chat_Free.obj").absolutePath, Dielectric(1.5)).triangles
        assertEquals(EXPECTED_SAMPLE_TRIANGLES, triangles.size)
    }

    private companion object {
        // 163 quads and 4 triangles
        const val EXPECTED_SAMPLE_TRIANGLES = 330
    }

    @Test
    fun `a face whose corners share one normal stays flat, others shade smoothly`() {
        val file = objFile(
            "v 0 0 0\nv 1 0 0\nv 0 1 0\nvn 0 0 1\nvn 0 0.6 0.8\nf 1//1 2//1 3//1\nf 1//1 2//1 3//2\n",
        )
        val (flat, smooth) = ObjParser(file.absolutePath, Lambert(Color(0.5, 0.5, 0.5))).triangles
        assertEquals(null, flat.normals)
        assertEquals(Point3(0.0, 0.6, 0.8), smooth.normals?.n2)
    }
}
