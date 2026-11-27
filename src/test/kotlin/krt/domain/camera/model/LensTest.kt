package krt.domain.camera.model

import kotlin.test.Test
import kotlin.test.assertEquals
import krt.platform.math.Point3

class LensTest {

    @Test
    fun `focal length on a 24 mm frame narrows as the field of view widens`() {
        assertEquals(12, Lens.focalLengthMm(90.0))
        assertEquals(26, Lens.focalLengthMm(50.0))
        assertEquals(68, Lens.focalLengthMm(20.0))
        assertEquals(3, Lens.focalLengthMm(150.0))
    }

    @Test
    fun `blur grows with the aperture`() {
        assertEquals("subtle blur", Lens.blur(0.1))
        assertEquals("soft blur", Lens.blur(0.2))
        assertEquals("strong blur", Lens.blur(1.0))
        assertEquals("extreme blur", Lens.blur(4.0))
    }

    @Test
    fun `focus on a point is its distance from the camera`() {
        assertEquals(5.0, Lens.focusOn(Point3(1.0, 1.0, 1.0), Point3(4.0, 5.0, 1.0)))
    }
}
