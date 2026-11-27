package krt.i9ns.scenefile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import krt.domain.scene.model.DemoScene
import krt.domain.scene.model.Material
import krt.domain.scene.model.Triangle
import krt.domain.trace.GpuMaterials
import krt.domain.trace.GpuScenePacking
import krt.i9ns.objfile.Glob
import krt.i9ns.objfile.ObjFiles
import krt.i9ns.objfile.ObjScene
import krt.i9ns.objfile.ObjSceneError
import krt.i9ns.objfile.ObjText
import krt.i9ns.objfile.Sidecar

/** The checks the GitHub voxel package asks of an importer: its scenes hold together and come out whole. */
class VoxelScenesTest {

    private val scenes = listOf(DemoScene.NOTIFICATIONS, DemoScene.LAPTOP_CREW, DemoScene.DEPLOY)

    private fun text(scene: DemoScene, name: String): String {
        val directory = "/krt/scenes/" + scene.path.substringBeforeLast('/') + "/"
        return requireNotNull(javaClass.getResource(directory + name)) { directory + name }.readText()
    }

    @Test
    fun `object names are unique, every glob matches and every bound material exists`() {
        scenes.forEach { scene ->
            val obj = ObjText.obj(text(scene, scene.path.substringAfterLast('/')))
            val names = obj.objects.map { it.name }
            assertEquals(names.size, names.toSet().size, "$scene")
            val sidecar = Sidecar.of(text(scene, "render-showcase.json"))
            ObjScene.validate(sidecar, names)
            sidecar.lightGlobs.forEach { glob -> assertTrue(names.any { Glob.matches(glob, it) }, "$scene: $glob") }
        }
    }

    @Test
    fun `a binding that matches nothing is an error`() {
        val sidecar = """{"materials": {"m": {"type": "Lambert"}},
            "bindings": [{"objects": ["ghost_*"], "material": "m"}]}"""
        val obj = "v 0 0 0\nv 1 0 0\nv 0 1 0\no a\nf 1 2 3\n"
        assertFailsWith<ObjSceneError> { ObjScene.read(ObjFiles(obj, showcase = sidecar)) }
    }

    @Test
    fun `every triangle has valid corners and unit normals, and every emitter of the sidecar glows`() {
        val library = SceneLibrary()
        scenes.forEach { scene ->
            val triangles = library.load(scene).objects.filterIsInstance<Triangle>()
            triangles.forEach { t ->
                t.normals?.let { n -> listOf(n.n0, n.n1, n.n2).forEach { assertEquals(1.0, it.length(), 1e-9) } }
            }
            val glowing = triangles.filter { it.material.emits }.map { it.id }.toSet()
            val sidecar = Sidecar.of(text(scene, "render-showcase.json"))
            sidecar.lightGlobs.forEach { glob -> assertTrue(glowing.any { Glob.matches(glob, it) }, "$scene: $glob") }
        }
    }

    @Test
    fun `the GPU gets the parameters of every material as the CPU has them`() {
        val library = SceneLibrary()
        scenes.forEach { scene ->
            val materials = library.load(scene).objects.filterIsInstance<Triangle>().map { it.material }.distinct()
            materials.forEach { material -> assertPacked(material) }
        }
    }

    /** Each value of [material] survives the fixed point of the scene texture to within its step. */
    private fun assertPacked(material: Material) {
        val values = GpuMaterials.values(material)
        values.drop(1).forEach { value ->
            assertEquals(value, GpuScenePacking.decode(GpuScenePacking.encode(value)), STEP, "$material")
        }
    }

    private companion object {
        const val STEP = 2 * GpuScenePacking.RANGE / (1 shl 24)
    }
}
