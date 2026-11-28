package krt.domain.camera

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import krt.platform.math.Point3

class CameraTest {

    private fun camera() = Camera(
        origin = Point3(0.0, 0.0, 0.0),
        target = Point3(1.0, 0.0, 0.0),
        vup = Point3(0.0, 1.0, 0.0),
        fov = 90.0,
        aperture = 0.0,
        focusDist = 1.0,
        imageWidth = 100,
        imageHeight = 100,
    )

    @Test
    fun `a new camera looks at its target`() {
        val camera = camera()
        assertEquals(java.awt.Point(50, 50), camera.project(Point3(5.0, 0.0, 0.0)))
        assertNull(camera.project(Point3(-5.0, 0.0, 0.0)))
    }

    @Test
    fun `the default camera looks from (1, 1, 1) at the centre of the scene`() {
        val camera = Camera.default()
        assertEquals(java.awt.Point(200, 200), camera.project(Point3(-1.0, -1.0, -1.0)))
        assertEquals(Point3(1.0, 1.0, 1.0), camera.origin)
    }

    @Test
    fun `a camera whose target is its origin looks along positive x`() {
        val camera = Camera(Point3.ZERO, Point3.ZERO, Point3(0.0, 1.0, 0.0), 90.0, 0.0, 1.0, 100, 100)
        assertEquals(java.awt.Point(50, 50), camera.project(Point3(5.0, 0.0, 0.0)))
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
        val center = camera.ray(0.5, 0.5, Random(0))
        assertEquals(Point3(0.0, 0.0, 0.0), center.origin)
        assertEquals(1.0, center.direction.unit().x, 1e-9)
    }

    @Test
    fun `changing the image size updates the aspect ratio of the rays`() {
        val resized = camera().apply { imageWidth = 200 }
        val fresh = Camera(
            origin = Point3(0.0, 0.0, 0.0),
            target = Point3(1.0, 0.0, 0.0),
            vup = Point3(0.0, 1.0, 0.0),
            fov = 90.0,
            aperture = 0.0,
            focusDist = 1.0,
            imageWidth = 200,
            imageHeight = 100,
        )
        assertEquals(fresh.ray(1.0, 0.5, Random(0)).direction, resized.ray(1.0, 0.5, Random(0)).direction)
    }

    @Test
    fun `image size is parsed only within the accepted range`() {
        assertEquals(640, Camera.parseImageSize(" 640 "))
        assertEquals(32, Camera.parseImageSize("32"))
        assertEquals(1024, Camera.parseImageSize("1024"))
        assertEquals(null, Camera.parseImageSize("31"))
        assertEquals(2048, Camera.parseImageSize("2048"))
        assertEquals(null, Camera.parseImageSize("2049"))
        assertEquals(null, Camera.parseImageSize("abc"))
        assertEquals(null, Camera.parseImageSize(""))
    }
}
