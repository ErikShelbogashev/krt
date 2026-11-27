package raytracer.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CameraTest {

    private fun camera() = Camera(
        origin = Point3(0.0, 0.0, 0.0),
        target = Point3(0.0, 0.0, -1.0),
        vup = Point3(0.0, 1.0, 0.0),
        fov = 90.0,
        aperture = 0.0,
        focusDist = 1.0,
        imageWidth = 100,
        imageHeight = 100,
    )

    @Test
    fun `initial camera looks along positive x because yaw is zero`() {
        val camera = camera()
        assertEquals(java.awt.Point(50, 50), camera.project(Point3(5.0, 0.0, 0.0)))
        assertNull(camera.project(Point3(-5.0, 0.0, 0.0)))
    }

    @Test
    fun `projection maps points to screen and flips y`() {
        val camera = camera()
        assertEquals(java.awt.Point(50, 0), camera.project(Point3(1.0, 1.0, 0.0)))
        assertEquals(java.awt.Point(100, 50), camera.project(Point3(1.0, 0.0, 1.0)))
    }

    @Test
    fun `yaw rotates view and moving forward follows it`() {
        val camera = camera()
        camera.adjustYaw(Math.PI / 2)
        assertEquals(java.awt.Point(50, 50), camera.project(Point3(0.0, 0.0, 5.0)))
        camera.movementSpeed = 1.0
        camera.moveForward()
        assertEquals(java.awt.Point(50, 0), camera.project(Point3(0.0, 1.0, 2.0)))
    }

    @Test
    fun `pitch is clamped below vertical`() {
        val camera = camera()
        camera.adjustPitch(10.0)
        val maxPitch = Math.PI / 2 - 0.01
        val forward = Point3(kotlin.math.cos(maxPitch), kotlin.math.sin(maxPitch), 0.0) * 5.0
        assertEquals(java.awt.Point(50, 50), camera.project(forward))
    }

    @Test
    fun `ray without aperture starts at origin and goes through image plane`() {
        val camera = camera()
        val center = camera.ray(0.5, 0.5)
        assertEquals(Point3(0.0, 0.0, 0.0), center.origin)
        assertEquals(1.0, center.direction.unit().x, 1e-9)
    }
}
