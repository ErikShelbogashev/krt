package krt.domain.trace

import kotlin.test.Test
import kotlin.test.assertEquals
import krt.domain.scene.model.Dielectric
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Metal
import krt.domain.scene.model.Sphere
import krt.domain.scene.model.Triangle
import krt.platform.math.Color
import krt.platform.math.Point3

class GpuScenePackingTest {

    @Test
    fun `fixed point keeps scene coordinates within half a step`() {
        val step = 2 * GpuScenePacking.RANGE / (1 shl 24)
        listOf(-1000.0, -0.2, 0.0, 0.123456, 1.5, 999.999).forEach { value ->
            val decoded = GpuScenePacking.decode(GpuScenePacking.encode(value))
            assertEquals(value, decoded, step / 2 + 1e-12)
        }
    }

    @Test
    fun `values outside the range are clamped`() {
        assertEquals(0, GpuScenePacking.encode(-1e9))
        assertEquals((1 shl 24) - 1, GpuScenePacking.encode(1e9))
    }

    @Test
    fun `spheres keep geometry and material, other objects are skipped`() {
        val objects = listOf(
            Sphere(Point3(1.0, 2.0, 3.0), 0.5, Lambert(Color(0.1, 0.2, 0.3))),
            Triangle(Point3.ZERO, Point3(1.0, 0.0, 0.0), Point3(0.0, 1.0, 0.0), Lambert(Color(1.0, 1.0, 1.0))),
            Sphere(Point3(-1.0, 0.0, 0.0), 2.0, Metal(Color(0.7, 0.6, 0.5), 0.3)),
            Sphere(Point3.ZERO, 1.0, Dielectric(1.5)),
            Sphere(Point3.ZERO, 1.0, Emissive(Color(0.1, 0.7, 0.4), 2.0)),
        )

        val scene = GpuScenePacking.pack(objects)

        assertEquals(4, scene.sphereCount)
        assertEquals(listOf(1f, 2f, 3f, 0.5f, -1f, 0f, 0f, 2f), scene.spheres.take(8))
        assertMaterial(scene, 0, GpuScenePacking.LAMBERT, 0.1, 0.2, 0.3, 0.0)
        assertMaterial(scene, 1, GpuScenePacking.METAL, 0.7, 0.6, 0.5, 0.3)
        assertMaterial(scene, 2, GpuScenePacking.DIELECTRIC, 1.0, 1.0, 1.0, 1.5)
        assertMaterial(scene, 3, GpuScenePacking.EMISSIVE, 0.2, 1.4, 0.8, 0.0)
    }

    @Suppress("LongParameterList")
    private fun assertMaterial(scene: GpuScene, sphere: Int, kind: Int, r: Double, g: Double, b: Double, p: Double) {
        val first = sphere * GpuScenePacking.MATERIAL_VALUES
        val values = (0 until GpuScenePacking.MATERIAL_VALUES).map { texel(scene, first + it) }
        listOf(kind.toDouble(), r, g, b, p).zip(values).forEach { (expected, actual) ->
            assertEquals(expected, actual, 1e-3)
        }
    }

    private fun texel(scene: GpuScene, index: Int): Double {
        val at = index * 4
        val code = (scene.materials[at].toInt() and 255 shl 16) or
            (scene.materials[at + 1].toInt() and 255 shl 8) or
            (scene.materials[at + 2].toInt() and 255)
        assertEquals(-1, scene.materials[at + 3].toInt())
        return GpuScenePacking.decode(code)
    }
}
