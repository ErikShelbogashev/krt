package krt.domain.scene.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import krt.domain.trace.PathTracing
import krt.domain.trace.TraceScene
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

class MotionBlurTest {

    private val ball = Sphere(Point3.ZERO, 0.5, Emissive(Color.ONE, 1.0), velocity = Point3(2.0, 0.0, 0.0))

    @Test
    fun `a moving ball is where its velocity takes it at the moment of the ray`() {
        val down = Point3(0.0, 0.0, -1.0)
        assertNotNull(ball.hit(Ray(Point3(0.0, 0.0, 5.0), down, time = 0.0), 0.001, 100.0))
        assertNull(ball.hit(Ray(Point3(0.0, 0.0, 5.0), down, time = 0.9), 0.001, 100.0))
        assertNotNull(ball.hit(Ray(Point3(1.8, 0.0, 5.0), down, time = 0.9), 0.001, 100.0))
        assertEquals(Point3(1.0, 0.0, 0.0), ball.centerAt(0.5))
    }

    @Test
    fun `the box of a moving ball holds all of its path`() {
        val bounds = ball.bounds()
        assertEquals(Point3(-0.5, -0.5, -0.5), bounds.min)
        assertEquals(Point3(2.5, 0.5, 0.5), bounds.max)
        assertEquals(ball.velocity, ball.translate(Point3(1.0, 0.0, 0.0)).velocity)
    }

    @Test
    fun `with motion blur a moving ball smears along its path`() {
        val look = SceneLook(Sky(Color.ZERO, Color.ZERO), fov = 60.0, aperture = 0.0)
        val still = coverage(TraceScene(listOf(ball), look))
        val blurred = coverage(TraceScene(listOf(ball), look.copy(motionBlur = true)))
        assertEquals(1.0, still, 1e-9)
        assertTrue(blurred in 0.3..0.6, "the ball covers the middle of its path for half the time: $blurred")
    }

    /** Share of rays through the middle of the ball's path, at x = 1.4, that see it. */
    private fun coverage(scene: TraceScene): Double {
        val random = Random(3)
        val ray = Ray(Point3(1.4, 0.0, 5.0), Point3(0.0, 0.0, -1.0))
        val start = Ray(Point3(0.2, 0.0, 5.0), Point3(0.0, 0.0, -1.0))
        val probe = if (scene.look.motionBlur) ray else start
        return (0 until 2000).count { PathTracing.color(scene, probe, 2, random).x > 0.5 } / 2000.0
    }
}
