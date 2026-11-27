package krt.domain.scene.model

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

class MicrofacetMaterialTest {

    private val down = Ray(Point3(0.3, 1.0, 0.2), Point3(-0.3, -1.0, -0.2))

    private fun hit(ray: Ray, material: Material) = Hit.create(Point3.ZERO, 1.0, ray, Point3(0.0, 1.0, 0.0), material)

    @Test
    fun `a smooth triangle blends its corner normals but keeps the side of its face`() {
        val normals = VertexNormals(Point3(0.0, 0.0, 1.0), Point3(1.0, 0.0, 1.0).unit(), Point3(0.0, 1.0, 1.0).unit())
        val corners = listOf(Point3.ZERO, Point3(1.0, 0.0, 0.0), Point3(0.0, 1.0, 0.0))
        val triangle = Triangle(corners[0], corners[1], corners[2], Lambert(GREY), normals = normals)
        val front = assertNotNull(triangle.hit(Ray(Point3(0.01, 0.01, 1.0), Point3(0.0, 0.0, -1.0)), 0.001, 10.0))
        assertTrue(front.isFrontFace)
        assertEquals(0.0, (front.normal - Point3(0.0, 0.0, 1.0)).length(), 0.02)
        val back = assertNotNull(triangle.hit(Ray(Point3(0.5, 0.4, -1.0), Point3(0.0, 0.0, 1.0)), 0.001, 10.0))
        assertTrue(!back.isFrontFace)
        assertEquals(normals.at(0.5, 0.4) * -1.0, back.normal)
    }

    @Test
    fun `vertex normals given against the winding still face the side the ray comes from`() {
        val against =
            VertexNormals(Point3(0.0, 0.0, -1.0), Point3(0.1, 0.0, -1.0).unit(), Point3(0.0, 0.1, -1.0).unit())
        val corners = listOf(Point3.ZERO, Point3(1.0, 0.0, 0.0), Point3(0.0, 1.0, 0.0))
        val triangle = Triangle(corners[0], corners[1], corners[2], Dielectric(1.5), normals = against)
        val hit = assertNotNull(triangle.hit(Ray(Point3(0.2, 0.2, 1.0), Point3(0.0, 0.0, -1.0)), 0.001, 10.0))
        assertTrue(hit.isFrontFace)
        assertTrue(hit.normal.z > 0.9, "normal ${hit.normal} faces away from the ray")
    }

    @Test
    fun `smoothing a sphere of flat faces gives normals pointing out from its centre`() {
        val sphere = Meshes.smooth(Meshes.icosphere(Pose(Point3.ZERO), 1.0, 2, Lambert(GREY)))
        sphere.forEach { triangle ->
            val normals = assertNotNull(triangle.normals)
            assertEquals(1.0, normals.n0.dot(triangle.v0.unit()), 1e-3)
        }
    }

    @Test
    fun `gold reflects by its complex refraction index head on`() {
        val gold = Metals.GOLD
        val reflectance = ConductorFresnel.reflectance(1.0, gold.eta, gold.k)
        val expected = { n: Double, k: Double -> ((n - 1) * (n - 1) + k * k) / ((n + 1) * (n + 1) + k * k) }
        assertEquals(expected(gold.eta.x, gold.k.x), reflectance.x, 1e-9)
        assertEquals(expected(gold.eta.z, gold.k.z), reflectance.z, 1e-9)
        assertTrue(reflectance.x > reflectance.z, "gold is yellow")
        assertEquals(1.0, ConductorFresnel.channel(0.0, gold.eta.y, gold.k.y), 1e-9)
    }

    @Test
    fun `a polished metal is a mirror, a rough one spreads its reflection`() {
        val polished = Conductor(Metals.SILVER, roughness = 0.0)
        val mirror = assertNotNull(polished.scatter(down, hit(down, polished), Random(1))).ray.direction
        assertEquals(0.0, (mirror.unit() - down.direction.unit().reflect(Point3(0.0, 1.0, 0.0))).length(), 1e-3)

        val rough = Conductor(Metals.SILVER, roughness = 0.6)
        val spread = (0 until 200).mapNotNull { rough.scatter(down, hit(down, rough), Random(it))?.ray?.direction }
        assertTrue(spread.all { it.y > 0 })
        assertTrue(spread.map { it.unit().x }.let { it.max() - it.min() } > 0.5)
    }

    @Test
    fun `anisotropic metal stretches its highlight along the tangent`() {
        val brushed = Conductor(Metals.ALUMINIUM, roughness = 0.5, anisotropy = 0.9)
        val straightDown = Ray(Point3(0.0, 0.0, 1.0), Point3(0.0, 0.0, -1.0))
        val facing = Hit.create(Point3.ZERO, 1.0, straightDown, Point3(0.0, 0.0, 1.0), brushed)
        val frame = Microfacets.frame(facing.normal)
        val directions = (0 until 2000).mapNotNull { brushed.scatter(straightDown, facing, Random(it))?.ray?.direction }
        val along = directions.sumOf { abs(it.unit().dot(frame.tangent)) }
        val across = directions.sumOf { abs(it.unit().dot(frame.bitangent)) }
        assertTrue(along > 1.5 * across, "along $along, across $across")
    }

    @Test
    fun `a metal brushed along a direction stretches its highlight along it`() {
        val brushed = Conductor(Metals.ALUMINIUM, roughness = 0.5, anisotropy = 0.9, brushing = Point3(0.0, 1.0, 0.0))
        val straightDown = Ray(Point3(0.0, 0.0, 1.0), Point3(0.0, 0.0, -1.0))
        val facing = Hit.create(Point3.ZERO, 1.0, straightDown, Point3(0.0, 0.0, 1.0), brushed)
        val directions = (0 until 2000).mapNotNull { brushed.scatter(straightDown, facing, Random(it))?.ray?.direction }
        val along = directions.sumOf { abs(it.unit().y) }
        val across = directions.sumOf { abs(it.unit().x) }
        assertTrue(along > 1.5 * across, "along $along, across $across")
        assertEquals(Point3(0.0, 1.0, 0.0), Microfacets.frame(Point3(0.0, 0.0, 1.0), Point3(0.0, 2.0, 0.5)).tangent)
    }

    @Test
    fun `visible normals face the viewer and shadowing never adds light`() {
        val facets = Microfacets.of(0.7, 0.5)
        val random = Random(2)
        val viewer = Point3(0.4, -0.2, 0.8).unit()
        repeat(500) {
            val normal = facets.visibleNormal(viewer, random)
            assertTrue(normal.z >= 0 && normal.dot(viewer) > -1e-9)
            val incoming = normal * (2 * viewer.dot(normal)) - viewer
            assertTrue(facets.shadowing(viewer, incoming) in 0.0..1.0)
        }
    }

    @Test
    fun `frosted glass scatters what it lets through and keeps it on the far side`() {
        val frosted = Dielectric(1.5, roughness = 0.5)
        val through = (0 until 400).mapNotNull { frosted.scatter(down, hit(down, frosted), Random(it)) }
            .filter { it.ray.direction.y < 0 }
        assertTrue(through.size > 200)
        assertTrue(through.map { it.ray.direction.unit().x }.let { it.max() - it.min() } > 0.3)
        assertTrue(through.all { it.attenuation.x <= 1.0 })
        val clear = Dielectric(1.5)
        assertNull(clear.scatter(down, hit(down, clear), Random(1))?.takeIf { it.attenuation != Point3.ONE })
    }

    private companion object {
        val GREY = Color(0.5, 0.5, 0.5)
    }
}
