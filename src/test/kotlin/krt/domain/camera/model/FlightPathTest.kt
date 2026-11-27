package krt.domain.camera.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import krt.domain.scene.model.DemoScene
import krt.platform.math.Point3

class FlightPathTest {

    private val path = Flights.of(DemoScene.SHOWCASE)

    @Test
    fun `the flight passes every keyframe`() {
        val steps = path.keyframes.size * 10
        path.keyframes.forEachIndexed { index, key ->
            val pose = path.pose(index * 10, steps)
            assertEquals(0.0, (pose.origin - key.origin).length(), 1e-9)
            assertEquals(0.0, (pose.target - key.target).length(), 1e-9)
        }
    }

    @Test
    fun `the flight is closed and smooth`() {
        val steps = 600
        val first = path.pose(0, steps)
        val last = path.pose(steps - 1, steps)
        assertTrue((first.origin - last.origin).length() < 0.5, "the last pose is next to the first")
        val moves = (1 until steps).map { (path.pose(it, steps).origin - path.pose(it - 1, steps).origin).length() }
        assertTrue(moves.max() < 0.5, "no jumps: ${moves.max()}")
    }

    @Test
    fun `flights stay above the floor`() {
        DemoScene.entries.forEach { scene ->
            val lowest = (0 until 600).minOf { Flights.of(scene).pose(it, 600).origin.y }
            assertTrue(lowest > 0.5, "$scene flies at $lowest")
        }
    }

    @Test
    fun `a flight needs keyframes`() {
        val two = listOf(FlightPose(Point3.ZERO, Point3.ONE), FlightPose(Point3.ONE, Point3.ZERO))
        assertTrue(runCatching { FlightPath(two) }.isFailure)
    }
}
