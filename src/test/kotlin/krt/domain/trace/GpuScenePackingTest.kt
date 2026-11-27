package krt.domain.trace

import kotlin.math.ln
import kotlin.math.roundToLong
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import krt.domain.scene.World
import krt.domain.scene.model.Checker
import krt.domain.scene.model.Dielectric
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.Fog
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Metal
import krt.domain.scene.model.Plastic
import krt.domain.scene.model.Sphere
import krt.domain.scene.model.Triangle
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

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
    fun `spheres keep geometry and material`() {
        val objects = listOf(
            Sphere(Point3(1.0, 2.0, 3.0), 0.5, Lambert(Color(0.1, 0.2, 0.3))),
            Sphere(Point3(-1.0, 0.0, 0.0), 2.0, Metal(Color(0.7, 0.6, 0.5), 0.3)),
            Sphere(Point3.ZERO, 1.0, Dielectric(1.5)),
            Sphere(Point3(5.0, 0.0, 0.0), 1.0, Emissive(Color(0.1, 0.7, 0.4), 2.0)),
        )

        val scene = Packed(GpuScenePacking.pack(objects))

        assertEquals(4, scene.scene.primitiveCount)
        val bySphere = (0 until 4).associateBy { slot -> scene.vector(scene.primitive(slot) + 1) }
        assertEquals(GpuScenePacking.SPHERE, scene.code(scene.primitive(bySphere.getValue(Point3(1.0, 2.0, 3.0)))))
        assertEquals(0.5, scene.number(scene.primitive(bySphere.getValue(Point3(1.0, 2.0, 3.0))) + 4), 1e-3)
        scene.assertMaterial(bySphere.getValue(Point3(1.0, 2.0, 3.0)), GpuScenePacking.LAMBERT, 0.1, 0.2, 0.3, 0.0)
        scene.assertMaterial(bySphere.getValue(Point3(-1.0, 0.0, 0.0)), GpuScenePacking.METAL, 0.7, 0.6, 0.5, 0.3)
        scene.assertMaterial(bySphere.getValue(Point3.ZERO), GpuScenePacking.DIELECTRIC, 0.0, 0.0, 0.0, 1.5)
        scene.assertMaterial(bySphere.getValue(Point3(5.0, 0.0, 0.0)), GpuScenePacking.EMISSIVE, 0.2, 1.4, 0.8, 0.0)
    }

    @Test
    fun `new materials pack their colours and parameters`() {
        val glass = Dielectric(1.5, tint = Color(0.5, 1.0, 1.0), density = 2.0)
        assertValues(listOf(2.0, 2 * ln(2.0), 0.0, 0.0, 1.5), GpuMaterials.values(glass).take(5))
        val plastic = Plastic(Color(0.8, 0.1, 0.1), roughness = 0.05)
        assertValues(listOf(4.0, 0.8, 0.1, 0.1, 0.05, 1.5), GpuMaterials.values(plastic).take(6))
        val checker = Checker(Color(0.9, 0.9, 0.9), Color(0.1, 0.1, 0.1), cellSize = 0.5, roughness = 0.1)
        assertValues(
            listOf(5.0, 0.9, 0.9, 0.9, 0.1, 1.5, 0.1, 0.1, 0.1, 0.5),
            GpuMaterials.values(checker).take(10),
        )
    }

    private fun assertValues(expected: List<Double>, actual: List<Double>) {
        expected.zip(actual).forEach { (e, a) -> assertEquals(e, a, 1e-9) }
        assertEquals(expected.size, actual.size)
    }

    @Test
    fun `triangles keep their first vertex and the edges from it`() {
        val triangle = Triangle(Point3(1.0, 0.0, 0.0), Point3(3.0, 0.0, 0.0), Point3(1.0, 2.0, 1.0), Lambert(Color.ONE))
        val scene = Packed(GpuScenePacking.pack(listOf(triangle)))
        val at = scene.primitive(0)
        assertEquals(GpuScenePacking.TRIANGLE, scene.code(at))
        val expected = listOf(Point3(1.0, 0.0, 0.0), Point3(2.0, 0.0, 0.0), Point3(0.0, 2.0, 1.0))
        assertEquals(expected, listOf(1, 4, 7).map { scene.vector(at + it) })
        scene.assertMaterial(0, GpuScenePacking.LAMBERT, 1.0, 1.0, 1.0, 0.0)
    }

    @Test
    fun `the walk of the shader over the packed BVH finds the hits of the CPU`() {
        val random = Random(9)
        val objects = World.default(Random(42)).objects + List(TRIANGLES) { randomTriangle(random) }
        val scene = Packed(GpuScenePacking.pack(objects))
        val cpu = TraceScene(objects)
        repeat(RAYS) {
            val ray = Ray(Point3.random(random, -RANGE, RANGE), Point3.random(random, -1.0, 1.0))
            val expected = cpu.hit(ray, T_MIN, Double.POSITIVE_INFINITY)?.t
            val actual = scene.walk(ray)
            if (expected == null) assertNull(actual) else assertEquals(expected, assertNotNull(actual), TOLERANCE)
        }
    }

    @Test
    fun `fog packs as a medium and emissive primitives as lights`() {
        val objects = listOf(
            Sphere(Point3(1.0, 0.0, 0.0), 0.5, Lambert(Color(0.1, 0.2, 0.3))),
            Fog(Point3(0.0, 2.0, 0.0), 1.5, 0.25, Color(0.9, 0.8, 0.7)),
            Sphere(Point3(5.0, 0.0, 0.0), 1.0, Emissive(Color(0.1, 0.7, 0.4), 2.0)),
            Triangle(Point3(0.0, 4.0, 0.0), Point3(1.0, 4.0, 0.0), Point3(0.0, 4.0, 1.0), Emissive(Color.ONE, 1.0)),
        )

        val scene = Packed(GpuScenePacking.pack(objects, sampleLights = true))

        val fog = (0 until 4).single { scene.code(scene.primitive(it)) == GpuScenePacking.MEDIUM }
        assertEquals(Point3(0.0, 2.0, 0.0), scene.vector(scene.primitive(fog) + 1))
        assertEquals(0.25, scene.number(scene.primitive(fog) + 5), 1e-3)
        scene.assertMaterial(fog, GpuScenePacking.ISOTROPIC, 0.9, 0.8, 0.7, 0.0)
        val lights = scene.scene.lights
        assertEquals(2, lights.sampled)
        val emissive = (0 until lights.count).map { scene.code(lights.start + it) }
        emissive.forEach { slot -> scene.assertMaterialKind(slot, GpuScenePacking.EMISSIVE) }
        assertEquals(0, GpuScenePacking.pack(objects).lights.sampled)
    }

    @Test
    fun `an empty scene packs no nodes`() {
        val scene = GpuScenePacking.pack(emptyList())
        assertEquals(0, scene.nodeCount)
        assertEquals(0, scene.primitiveCount)
    }

    /** Reads the packed texture as the shader does. */
    private class Packed(val scene: GpuScene) {

        fun code(index: Int): Int {
            val at = index * 4
            assertEquals(-1, scene.data[at + 3].toInt())
            return (scene.data[at].toInt() and 255 shl 16) or
                (scene.data[at + 1].toInt() and 255 shl 8) or
                (scene.data[at + 2].toInt() and 255)
        }

        fun number(index: Int): Double = GpuScenePacking.decode(code(index))

        fun vector(index: Int): Point3 = Point3(number(index), number(index + 1), number(index + 2)).rounded()

        fun primitive(slot: Int): Int = scene.primitiveStart + slot * GpuScenePacking.PRIMITIVE_VALUES

        @Suppress("LongParameterList")
        fun assertMaterial(slot: Int, kind: Int, r: Double, g: Double, b: Double, p: Double) {
            val at = scene.materialStart + slot * GpuScenePacking.MATERIAL_VALUES
            assertEquals(kind, code(at))
            listOf(r, g, b, p).forEachIndexed { i, expected -> assertEquals(expected, number(at + 1 + i), 1e-3) }
        }

        fun assertMaterialKind(slot: Int, kind: Int) =
            assertEquals(kind, code(scene.materialStart + slot * GpuScenePacking.MATERIAL_VALUES))

        /** The stackless walk of path_tracer.sksl, over spheres. */
        fun walk(ray: Ray): Double? {
            var closest = Double.POSITIVE_INFINITY
            var node = 0
            while (node < scene.nodeCount) {
                val base = node * GpuScenePacking.NODE_VALUES
                if (!entersBox(base, ray, closest)) {
                    node = code(base + 6)
                    continue
                }
                val first = code(base + 7)
                for (slot in first until first + code(base + 8)) {
                    hit(primitive(slot), ray, closest)?.let { closest = it }
                }
                node++
            }
            return closest.takeIf { it.isFinite() }
        }

        private fun entersBox(base: Int, ray: Ray, far: Double): Boolean {
            var near = T_MIN
            var exit = far
            for (axis in 0 until 3) {
                val t1 = (number(base + axis) - ray.origin[axis]) / ray.direction[axis]
                val t2 = (number(base + 3 + axis) - ray.origin[axis]) / ray.direction[axis]
                near = maxOf(near, minOf(t1, t2))
                exit = minOf(exit, maxOf(t1, t2))
            }
            return exit >= near
        }

        private fun hit(base: Int, ray: Ray, tMax: Double): Double? =
            if (code(base) == GpuScenePacking.TRIANGLE) triangleHit(base, ray, tMax) else sphereHit(base, ray, tMax)

        private fun triangleHit(base: Int, ray: Ray, tMax: Double): Double? {
            val v0 = vector(base + 1)
            val triangle = Triangle(v0, v0 + vector(base + 4), v0 + vector(base + 7), Lambert(Color.ONE))
            return triangle.hit(ray, T_MIN, tMax)?.t
        }

        private fun sphereHit(base: Int, ray: Ray, tMax: Double): Double? {
            val oc = ray.origin - Point3(number(base + 1), number(base + 2), number(base + 3))
            val radius = number(base + 4)
            val a = ray.direction.dot(ray.direction)
            val halfB = oc.dot(ray.direction)
            val discriminant = halfB * halfB - a * (oc.dot(oc) - radius * radius)
            if (discriminant < 0) return null
            val near = (-halfB - sqrt(discriminant)) / a
            val far = (-halfB + sqrt(discriminant)) / a
            return listOf(near, far).firstOrNull { it in T_MIN..tMax }
        }

        private fun Point3.rounded() = Point3(round(x), round(y), round(z))

        private fun round(value: Double) = (value * PRECISION).roundToLong() / PRECISION
    }

    private fun randomTriangle(random: Random): Triangle {
        val corner = Point3.random(random, -RANGE, RANGE)
        val edge = { Point3.random(random, -1.0, 1.0) }
        return Triangle(corner, corner + edge(), corner + edge(), Lambert(Color.ONE))
    }

    private companion object {
        const val RAYS = 500
        const val RANGE = 12.0
        const val T_MIN = 0.001
        const val TOLERANCE = 1e-2
        const val TRIANGLES = 200
        const val PRECISION = 1e4
    }
}
