package krt.domain.scene.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import krt.platform.math.Point3

class PhaseFunctionTest {

    @Test
    fun `the phase function shares out all the scattered light, whatever its g`() {
        listOf(-0.4, 0.0, 0.5, 0.85).forEach { g ->
            val steps = 20_000
            // Over the sphere, by the cosine: dω = 2π d(cosθ).
            val total = (0 until steps).sumOf { i ->
                val cosine = -1 + 2 * (i + 0.5) / steps
                HenyeyGreenstein.density(cosine, g) * 2 * PI * (2.0 / steps)
            }
            assertEquals(1.0, total, 1e-3, "g = $g")
        }
    }

    @Test
    fun `isotropic scattering is the same in every direction`() {
        assertEquals(1 / (4 * PI), HenyeyGreenstein.density(0.3, 0.0), 1e-15)
        assertEquals(1 / (4 * PI), HenyeyGreenstein.density(-0.9, 0.0), 1e-15)
    }

    @Test
    fun `sampled directions are unit vectors whose mean cosine to the incoming one is g`() {
        val random = Random(3)
        val incoming = Point3(0.3, -0.5, 0.8).unit()
        listOf(0.0, 0.6, 0.85, -0.3).forEach { g ->
            val samples = List(200_000) {
                HenyeyGreenstein.sample(incoming, g, random.nextDouble(), random.nextDouble())
            }
            assertTrue(samples.all { abs(it.length() - 1) < 1e-9 }, "g = $g: not unit")
            val mean = samples.sumOf { it.dot(incoming) } / samples.size
            assertEquals(g, mean, 0.01, "g = $g")
        }
    }
}
