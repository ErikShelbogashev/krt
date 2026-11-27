package krt.platform.math

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Point3RandomTest {

    @Test
    fun `points in the unit sphere fill all octants around the origin`() {
        val random = Random(1)
        val points = List(SAMPLES) { Point3.randomInUnitSphere(random) }

        assertTrue(points.all { it.length() < 1.0 })
        val octants = points.map { Triple(it.x < 0, it.y < 0, it.z < 0) }.toSet()
        assertEquals(OCTANTS, octants.size, "octants hit")
        assertCentredAtOrigin(points)
    }

    @Test
    fun `points in the unit disk fill all quadrants around the origin`() {
        val random = Random(2)
        val points = List(SAMPLES) { Point3.randomInUnitDisk(random) }

        assertTrue(points.all { it.length() < 1.0 && it.z == 0.0 })
        val quadrants = points.map { (it.x < 0) to (it.y < 0) }.toSet()
        assertEquals(QUADRANTS, quadrants.size, "quadrants hit")
        assertCentredAtOrigin(points)
    }

    private fun assertCentredAtOrigin(points: List<Point3>) {
        val mean = points.fold(Point3.ZERO) { acc, p -> acc + p } / points.size.toDouble()
        assertTrue(abs(mean.x) < TOLERANCE && abs(mean.y) < TOLERANCE && abs(mean.z) < TOLERANCE, "mean $mean")
    }

    private companion object {
        const val SAMPLES = 20_000
        const val OCTANTS = 8
        const val QUADRANTS = 4
        const val TOLERANCE = 0.02
    }
}
