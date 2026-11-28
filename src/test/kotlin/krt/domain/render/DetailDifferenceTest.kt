package krt.domain.render

import kotlin.test.Test
import kotlin.test.assertTrue

/** Small changes with known support exercise the distinct spatial, image-wide and outlier budgets. */
class DetailDifferenceTest {
    private val expected = IntArray(DetailView.WIDTH * DetailView.BAND) { GRAY }

    @Test
    fun `a sparse machine difference is tolerated but an eight pixel red scratch is visible`() {
        val actual = expected.copyOf()
        actual[0] = RED_SHIFT
        assertTrue(DetailDifference.compare(expected, actual, 0).isEmpty())
        for (x in 0 until 8) actual[x] = RED_SHIFT
        val issues = DetailDifference.compare(expected, actual, 0)
        assertTrue(issues.any { it.startsWith("tile") })
        assertTrue(issues.none { it.startsWith("band") })
    }

    @Test
    fun `a one level grey shift exceeds the band budget`() {
        val actual = IntArray(expected.size) { GRAY + 0x010101 }
        val issues = DetailDifference.compare(expected, actual, 0)
        assertTrue(issues.any { it.startsWith("band") })
        assertTrue(issues.none { it.startsWith("tile") })
    }

    @Test
    fun `distributed bright errors exceed the outlier budget`() {
        val actual = expected.copyOf()
        for (x in 0..64 step 8) actual[x] = RED_SHIFT
        val issues = DetailDifference.compare(expected, actual, 0)
        assertTrue(issues.any { it.startsWith("band") })
        assertTrue(issues.none { it.startsWith("tile") })
    }

    private companion object {
        const val GRAY = 0x505050
        const val RED_SHIFT = 0xB05050
    }
}
