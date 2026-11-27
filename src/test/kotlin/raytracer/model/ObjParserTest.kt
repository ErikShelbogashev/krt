package raytracer.model

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class ObjParserTest {

    private fun objFile(content: String): File =
        File.createTempFile("krt", ".obj").apply {
            deleteOnExit()
            writeText(content)
        }

    @Test
    fun `quad face is split into two triangles and vertices are normalized`() {
        // Current parser expects the 3ds Max export format: "v" followed by two spaces, and a trailing space after the
        // fourth face index (a face is recognised as a quad only when splitting yields 5 parts).
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
    fun `triangle faces and quads without trailing space are currently ignored`() {
        val file = objFile("v  0 0 0\nv  1 0 0\nv  0 1 0\nv  1 1 0\nf 1 2 3 \nf 1 2 4 3\n")
        assertEquals(0, ObjParser(file.absolutePath, Lambert(Color(0.5, 0.5, 0.5))).triangles.size)
    }

    @Test
    fun `bundled sample model parses`() {
        val triangles = ObjParser(File("samples/ORIGAMI_Chat_Free.obj").absolutePath, Dielectric(1.5)).triangles
        assertEquals(EXPECTED_SAMPLE_TRIANGLES, triangles.size)
    }

    private companion object {
        const val EXPECTED_SAMPLE_TRIANGLES = 326
    }
}
