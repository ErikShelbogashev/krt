package krt.domain.scene.model

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import krt.platform.math.PixelDifferentials
import krt.platform.math.Point3

class WoodGrainTest {
    private val normal = Point3(0, 1, 0)
    private val wood = Pattern(PatternKind.WOOD, Point3.ZERO, Point3.ONE, turbulence = 0.0)

    @Test
    fun `ray differentials follow both pixel axes along a grazing plane`() {
        val direction = Point3(0.2, -0.07, 1)
        val dx = Point3(0.006, 0.0, 0)
        val dy = Point3(0.0, 0.006, 0)
        fun point(d: Point3) = d * (-1 / d.y)
        val surface = PixelDifferentials(dx, dy).onSurface(direction, normal, -1 / direction.y)
        val h = 0.0001
        for ((axis, projected) in listOf(dx to surface.dx, dy to surface.dy)) {
            val measured = (point(direction + axis * h) - point(direction - axis * h)) / (2 * h)
            assertTrue((measured - projected).length() < 1e-8)
            assertEquals(0.0, projected.dot(normal), 1e-12)
        }
        assertTrue(surface.dy.length() > surface.dx.length() * 10)
    }

    @Test
    fun `integrating annual rings preserves mean across negative phases and many periods`() {
        for (phase in listOf(0.01, 0.71, 0.85, 0.99)) for (span in listOf(0.02, 0.24, 1.0, 3.4, 32.0)) {
            // Positive radius throughout; subtract identical fine fibres using fibre weight zero.
            val center = Point3(0, 0, 50 + phase)
            val zero = WoodFilter(0.0, 0.0)
            val expected = (0 until 8192).sumOf { i ->
                wood.valueAt(center + Point3(0, 0, span * ((i + 0.5) / 8192 - 0.5)), zero)
            } / 8192
            val actual = wood.valueAt(center, WoodFilter(span, 0.0))
            assertEquals(expected, actual, 1e-6, "$phase ± ${span / 2}")
        }
    }

    @Test
    fun `subpixel bump follows near grain and averages over a distant complete ring`() {
        val point = Point3(0.13, 0, 1.84)
        val near = WoodGrain.filter(wood, point, PixelDifferentials(Point3(0.0001, 0, 0), Point3(0, 0, 0.0001)))
        val far = WoodGrain.filter(wood, point, PixelDifferentials(Point3(0.1, 0, 0), Point3(0, 0, 1)))
        val pointNormal = Bump.normal(wood, point, normal, 0.01)
        fun filtered(filter: WoodFilter) = Bump.normal(wood, point, normal, 0.01, filter, wood.valueAt(point, filter))
        assertTrue((pointNormal - normal).length() > 0.01)
        assertTrue((pointNormal - filtered(near)).length() < 0.001)
        val mean = (0 until 1024).fold(Point3.ZERO) { sum, i ->
            sum + Bump.normal(wood, point + Point3(0, 0, (i + 0.5) / 1024 - 0.5), normal, 0.01)
        }.unit()
        assertTrue(abs(filtered(far).z - mean.z) < 0.005)
        assertTrue((filtered(far) - normal).length() < 1e-8)
    }
}
