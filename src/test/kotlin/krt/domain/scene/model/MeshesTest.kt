package krt.domain.scene.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

class MeshesTest {

    private val material = Lambert(Color(0.5, 0.5, 0.5))
    private val pose = Pose(Point3(1.0, 2.0, 3.0))

    private fun List<Triangle>.vertices() = flatMap { listOf(it.v0, it.v1, it.v2) }

    @Test
    fun `an icosphere has 20 times 4 to the subdivisions faces on its sphere`() {
        val gem = Meshes.icosphere(pose, 2.0, 2, material)
        assertEquals(320, gem.size)
        gem.vertices().forEach { assertEquals(2.0, (it - pose.center).length(), 1e-9) }
    }

    @Test
    fun `a torus lies around the y axis of its pose`() {
        val ring = Meshes.torus(pose, 1.0, 0.25, 24, 8, material)
        assertEquals(24 * 8 * 2, ring.size)
        ring.vertices().forEach { vertex ->
            val local = vertex - pose.center
            val fromCircle = Point3(local.x, 0.0, local.z).length() - 1.0
            assertEquals(0.25, kotlin.math.sqrt(fromCircle * fromCircle + local.y * local.y), 1e-2)
        }
    }

    @Test
    fun `a torus knot is a closed tube a ray can hit`() {
        val knot = Meshes.torusKnot(Pose(Point3.ZERO), 0.4, 0.15, 2, 3, 120, 8, material)
        assertEquals(120 * 8 * 2, knot.size)
        val world = krt.domain.trace.TraceScene(knot)
        assertNotNull(world.hit(Ray(Point3(5.0, 0.0, 0.0), Point3(-1.0, 0.0, 0.0)), 0.001, Double.POSITIVE_INFINITY))
        assertTrue(knot.vertices().all { it.length() < 0.4 * 3 + 0.15 + 1e-9 })
    }

    @Test
    fun `a panel is two triangles that face the pose's z axis`() {
        val panel = Meshes.panel(pose, 2.0, 1.0, material)
        assertEquals(2, panel.size)
        val ray = Ray(pose.center + Point3(0.5, 0.5, 5.0), Point3(0.0, 0.0, -1.0))
        assertNotNull(krt.domain.trace.TraceScene(panel).hit(ray, 0.001, 10.0))
        val beside = Ray(pose.center + Point3(2.5, 0.0, 5.0), Point3(0.0, 0.0, -1.0))
        assertNull(krt.domain.trace.TraceScene(panel).hit(beside, 0.001, 10.0))
    }
}
