package krt.i9ns.pbrt

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import krt.domain.camera.model.ViewPreset
import krt.domain.scene.model.Conductor
import krt.domain.scene.model.DemoScene
import krt.domain.scene.model.DemoScenes
import krt.domain.scene.model.Dielectric
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.Fog
import krt.domain.scene.model.Fractal
import krt.domain.scene.model.FractalKind
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Metals
import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Sphere
import krt.domain.scene.model.StartView
import krt.domain.scene.model.Traceable
import krt.domain.scene.model.Triangle
import krt.platform.math.Color
import krt.platform.math.Point3

class PbrtTest {

    private val overview = StartView(ViewPreset.OVERVIEW.origin, ViewPreset.OVERVIEW.target)

    @Test
    fun `the classic scene comes back from its file exactly`() {
        val classic = DemoScenes.classic(Random(1))
        val written = PbrtWriter.write(classic, SceneLook.CLASSIC, overview, "classic")
        val read = PbrtReader.read(written.text, written.files)
        assertEquals(classic.size, read.objects.size)
        classic.zip(read.objects).forEach { (a, b) ->
            a as Sphere
            b as Sphere
            assertEquals(a.center, b.center)
            assertEquals(a.radius, b.radius)
            assertEquals(PbrtWriter.material(a.material), PbrtWriter.material(b.material))
        }
        assertEquals(SceneLook.CLASSIC.copy(start = overview), read.look.copy(sky = SceneLook.CLASSIC.sky))
        assertEquals(SceneLook.CLASSIC.sky, read.look.sky)
        assertTrue(read.warnings.isEmpty(), "${read.warnings}")
    }

    @Test
    fun `writing what was read gives the same file, for every demo scene`() {
        DemoScene.entries.forEach { scene ->
            val description = krt.i9ns.scenefile.SceneLibrary().load(scene)
            val first = PbrtWriter.write(description.objects, description.look, overview, "scene")
            val read = PbrtReader.read(first.text, first.files)
            val second = PbrtWriter.write(read.objects, read.look, read.look.start ?: overview, "scene")
            assertEquals(first.text, second.text, "$scene")
            assertEquals(first.files.keys, second.files.keys)
            assertTrue(read.warnings.isEmpty(), "$scene: ${read.warnings}")
        }
    }

    @Test
    fun `a scene written for pbrt-v4 is read with camera, meshes with normals, media and black-body lights`() {
        val text = """
            # a plain pbrt-v4 scene
            LookAt 0 1 5  0 1 0  0 1 0
            Camera "perspective" "float fov" [ 45 ] "float lensradius" 0.05
            WorldBegin
            LightSource "infinite" "rgb L" [ 0.1 0.2 0.3 ]
            MakeNamedMedium "smoke" "string type" "homogeneous" "rgb sigma_a" [0.1 0.1 0.1] "rgb sigma_s" [0.3 0.3 0.3]
                "float g" 0.7
            AttributeBegin
              AreaLightSource "diffuse" "blackbody L" [ 2700 ] "float scale" 5
              Translate 0 4 0
              Shape "sphere" "float radius" 0.5
            AttributeEnd
            AttributeBegin
              MediumInterface "smoke" ""
              Material "interface"
              Shape "sphere" "float radius" 3
            AttributeEnd
            AttributeBegin
              Material "conductor" "spectrum eta" "metal-Au-eta" "spectrum k" "metal-Au-k" "float roughness" 0.01
              Translate 1 0 0
              Scale 2 2 2
              Shape "trianglemesh" "integer indices" [0 1 2] "point3 P" [0 0 0  1 0 0  0 1 0]
                  "normal N" [0 0 1  0 0 1  0 0 1]
            AttributeEnd
        """.trimIndent()
        val scene = PbrtReader.read(text)
        assertEquals(StartView(Point3(0.0, 1.0, 5.0), Point3(0.0, 1.0, 0.0)), scene.look.start)
        assertEquals(45.0, scene.look.fov)
        assertEquals(0.1, scene.look.aperture, 1e-12)
        assertEquals(Color(0.1, 0.2, 0.3), scene.look.sky.zenith)
        val lamp = assertIs<Sphere>(scene.objects[0])
        assertEquals(Point3(0.0, 4.0, 0.0), lamp.center)
        assertIs<Emissive>(lamp.material)
        val fog = assertIs<Fog>(scene.objects[1])
        assertEquals(Color(0.4, 0.4, 0.4), fog.medium.density)
        assertEquals(0.75, fog.medium.albedo.x, 1e-12)
        assertEquals(0.7, fog.medium.g)
        val triangle = assertIs<Triangle>(scene.objects[2])
        assertEquals(Point3(3.0, 0.0, 0.0), triangle.v1)
        assertEquals(Point3(0.0, 0.0, 1.0), triangle.normals?.n0)
        val gold = assertIs<Conductor>(triangle.material)
        assertEquals(Metals.GOLD.eta, gold.eta)
    }

    @Test
    fun `krt lines stand before what pbrt draws instead, and pbrt sees only comments`() {
        val text = """
            WorldBegin
            #krt Name "Bulb"
            #krt Shape "fractal" "string kind" "mandelbulb" "float radius" 1.5
            Shape "sphere" "float radius" 0.7
            #krt Material "velvet" "rgb base" [0.3 0 0.1] "rgb sheen" [1 1 1] "float roughness" 0.4
            Material "diffuse" "rgb reflectance" [0.3 0 0.1]
            Shape "sphere" "float radius" 1
        """.trimIndent()
        val scene = PbrtReader.read(text)
        val bulb = assertIs<Fractal>(scene.objects[0])
        assertEquals(FractalKind.MANDELBULB, bulb.kind)
        assertEquals("Bulb", bulb.id)
        assertEquals(1.5, bulb.radius)
        assertIs<krt.domain.scene.model.Velvet>((scene.objects[1] as Sphere).material)
    }

    @Test
    fun `glass with a spectrum of indices keeps its dispersion`() {
        val glass = Dielectric(1.55, dispersion = 0.07)
        val lines = PbrtWriter.material(glass)
        val text = "WorldBegin\n${lines.joinToString("\n")}\nShape \"sphere\""
        val read = PbrtReader.read(text).objects.single() as Sphere
        val dielectric = assertIs<Dielectric>(read.material)
        assertEquals(1.55, dielectric.indexOfReflection, 1e-9)
        assertEquals(0.07, dielectric.dispersion, 1e-9)
        assertEquals(1.5168 to 0.0042, PbrtReader.read(
            "WorldBegin\nMaterial \"dielectric\" \"spectrum eta\" \"glass-BK7\"\nShape \"sphere\"",
        ).objects.single().let { (it as Sphere).material as Dielectric }.let { it.indexOfReflection to it.dispersion })
    }

    @Test
    fun `a broken file names its line`() {
        val error = assertFailsWith<PbrtError> { PbrtReader.read("WorldBegin\nAttributeEnd\n") }
        assertEquals(2, error.line)
        val missing = "WorldBegin\nShape \"plymesh\" \"string filename\" \"missing.ply\""
        assertFailsWith<PbrtError> { PbrtReader.read(missing) }
        assertTrue(PbrtReader.read("WorldBegin\nShape \"curve\"").warnings.single().contains("curve"))
    }

    @Test
    fun `PLY meshes come back from bytes, binary or text`() {
        val mesh = PlyMesh(
            listOf(Point3.ZERO, Point3(1.0, 0.0, 0.0), Point3(0.0, 1.0, 0.0)),
            List(3) { Point3(0.0, 0.0, 1.0) },
            listOf(intArrayOf(0, 1, 2)),
        )
        val read = Ply.read(Ply.write(mesh))
        assertEquals(mesh.points, read.points)
        assertEquals(mesh.normals, read.normals)
        assertEquals(listOf(0, 1, 2), read.triangles.single().toList())
        val ascii = "ply\nformat ascii 1.0\nelement vertex 4\nproperty float x\nproperty float y\nproperty float z\n" +
            "element face 1\nproperty list uchar int vertex_indices\nend_header\n0 0 0\n1 0 0\n1 1 0\n0 1 0\n4 0 1 2 3\n"
        assertEquals(2, Ply.read(ascii.toByteArray()).triangles.size)
    }

    @Test
    fun `references name the files a scene needs`() {
        val text = "Include \"lights.pbrt\"\nWorldBegin\nShape \"plymesh\" \"string filename\" \"knot.ply\""
        assertEquals(listOf("lights.pbrt", "knot.ply"), PbrtReader.references(text))
    }

    private fun Traceable.center(): Point3 = bounds().center

    @Suppress("unused")
    private val unusedLambert = Lambert(Color.ONE)
}
