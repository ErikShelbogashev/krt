package krt.domain.scene.model

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

class PatternTest {

    @Test
    fun `noise is 0 at the lattice and smooth between`() {
        assertEquals(0.0, Noise.perlin(Point3(3.0, -2.0, 7.0)), 1e-12)
        val a = Noise.perlin(Point3(0.3, 0.4, 0.5))
        val b = Noise.perlin(Point3(0.3001, 0.4, 0.5))
        assertTrue(abs(a - b) < 1e-3)
        val random = Random(1)
        val values = (0 until 2000).map { Noise.perlin(Point3.random(random, -50.0, 50.0)) }
        assertTrue(values.all { it in -1.2..1.2 })
        assertTrue(values.max() - values.min() > 1.0)
    }

    @Test
    fun `noise repeats every 289 cells, as the GPU's does`() {
        val p = Point3(0.25, 1.5, 2.75)
        assertEquals(Noise.perlin(p), Noise.perlin(p + Point3(289.0, -289.0, 578.0)), 1e-9)
    }

    @Test
    fun `every pattern blends its two colours`() {
        val random = Random(2)
        PatternKind.entries.forEach { kind ->
            val pattern = Pattern(kind, Color(1.0, 0.0, 0.0), Color(0.0, 0.0, 1.0), scale = 2.0)
            val values = (0 until 500).map { pattern.valueAt(Point3.random(random, -3.0, 3.0)) }
            assertTrue(values.all { it in 0.0..1.0 }, "$kind")
            assertTrue(values.max() - values.min() > 0.3, "$kind varies")
            val color = pattern.colorAt(Point3(0.3, 0.2, 0.1))
            assertEquals(1.0, color.x + color.z, 1e-9)
        }
    }

    @Test
    fun `wood grows in rings around a trunk along the x axis, stripes along the grain`() {
        val wood = Pattern(PatternKind.WOOD, Color(0.6, 0.4, 0.2), Color(0.3, 0.15, 0.05), turbulence = 0.0)
        val ringsOnly = { p: Point3 -> wood.valueAt(p) - wood.valueAt(Point3(p.x, -p.y, -p.z)) }
        assertEquals(0.0, ringsOnly(Point3(0.3, 0.0, 0.4)), 0.3)
        val across = (0 until 200).map { wood.valueAt(Point3(0.0, 0.0, it * 0.01)) }
        val along = (0 until 200).map { wood.valueAt(Point3(it * 0.01, 0.0, 0.33)) }
        assertTrue(across.max() - across.min() > 2 * (along.max() - along.min()), "stripes run along x")
    }

    @Test
    fun `a bump tilts the normal along the pattern's slope`() {
        val marble = Pattern(PatternKind.MARBLE, Color(0.9, 0.9, 0.9), Color(0.1, 0.1, 0.1), scale = 3.0)
        val up = Point3(0.0, 1.0, 0.0)
        val tilted = (0 until 50).map { Bump.normal(marble, Point3(it * 0.037, 0.0, 0.11), up, 0.5) }
        assertTrue(tilted.all { abs(it.length() - 1) < 1e-9 && it.y > 0 })
        assertTrue(tilted.any { it.dot(up) < 0.99 })
        assertEquals(up, Bump.normal(Pattern(PatternKind.NOISE, Color.ONE, Color.ONE), Point3.ZERO, up, 0.0))
    }

    @Test
    fun `a bumped surface reports the bent normal for direct light`() {
        val material = Patterned(Pattern(PatternKind.MARBLE, Color.ONE, Color(0.2, 0.2, 0.2), 3.0), 0.1, bump = 0.8)
        val ray = Ray(Point3(0.13, 1.0, 0.07), Point3(0.0, -1.0, 0.0))
        val hit = Hit.create(Point3(0.13, 0.0, 0.07), 1.0, ray, Point3(0.0, 1.0, 0.0), material)
        val diffuse = (0 until 50).mapNotNull { material.scatter(ray, hit, Random(it)) }
            .first { it.lobe == Lobe.DIFFUSE }
        assertEquals(Bump.normal(material.pattern, hit.p, hit.normal, 0.8), diffuse.normal)
    }
}
