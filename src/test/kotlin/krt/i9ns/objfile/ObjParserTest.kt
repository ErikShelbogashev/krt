package krt.i9ns.objfile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Triangle
import krt.platform.math.Point3

class ObjParserTest {

    @Test
    fun `an OBJ uses the MTL its mtllib names, or else the one of its own name`() {
        assertEquals("parts.mtl", ObjScene.materialLibrary("# scene\nmtllib parts.mtl \nv 0 0 0\n", "scene.obj"))
        assertEquals("SCENE.mtl", ObjScene.materialLibrary("v 0 0 0\n", "SCENE.OBJ"))
        assertEquals("a.object.mtl", ObjScene.materialLibrary("v 0 0 0\n", "a.object.obj"))
    }


    @Test
    fun `faces keep their object names, corners and normals, quads cut into fans`() {
        val obj = ObjText.obj(
            "v 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nvn 0 0 1\nvn 0 0.6 0.8\n" +
                "o plate\nusemtl red\nf 1//1 2//1 3//1 4//1\no tile\nf -4/1/2 -3/2/1 -2/3/1\n",
        )
        assertEquals(listOf("plate", "tile"), obj.objects.map { it.name })
        assertEquals("red", obj.objects[0].material)
        assertEquals(2, obj.objects[0].faces.size)
        assertEquals(listOf(0, 2, 3), obj.objects[0].faces[1].points.toList())
        assertEquals(listOf(1, 0, 0), obj.objects[1].faces[0].normals.toList())
        assertEquals(1.0, obj.normals[1].length(), 1e-12)
    }

    @Test
    fun `an index out of range names its line`() {
        val error = assertFailsWith<IllegalArgumentException> { ObjText.obj("v 0 0 0\nf 1 2 3\n") }
        assertTrue(error.message!!.contains("line 2"))
    }

    @Test
    fun `MTL colours stand in where no binding says otherwise, and faces with one normal stay flat`() {
        val scene = ObjScene.read(
            ObjFiles(
                "mtllib s.mtl\nv 0 0 0\nv 1 0 0\nv 0 1 0\nvn 0 0 1\no lamp\nusemtl glow\nf 1//1 2//1 3//1\n" +
                    "o wall\nusemtl red\nf 1 2 3\n",
                "newmtl glow\nKd 0 0 0\nKe 2 2 2\nnewmtl red\nKd 0.8 0.1 0.1\n",
            ),
        )
        val (lamp, wall) = scene.objects.map { it as Triangle }
        assertIs<Emissive>(lamp.material)
        assertEquals("lamp", lamp.id)
        assertNull(lamp.normals)
        assertEquals(Point3(0.8, 0.1, 0.1), (wall.material as Lambert).albedo)
    }

    @Test
    fun `a Z-up scene turns Y-up once, points and normals alike`() {
        val layout = """{"coordinate_system": "right-handed, Z-up", "camera_suggestion": {"position": [0, -10, 2],
            "target": [0, 0, 1], "focal_length_mm": 50}, "objects": []}"""
        val scene = ObjScene.read(
            ObjFiles("v 0 0 0\nv 1 0 0\nv 0 0 1\nvn 0 -1 0\nvn 0 -1 0.1\no wall\nf 1//1 2//1 3//2\n", layout = layout),
        )
        val wall = scene.objects.single() as Triangle
        assertEquals(1.0, wall.v2.y, 1e-12)
        assertEquals(1.0, wall.normals!!.n0.z, 1e-12)
        assertEquals(10.0, scene.look.start!!.origin.z, 1e-12)
        assertEquals(2.0, scene.look.start!!.origin.y, 1e-12)
    }
}
