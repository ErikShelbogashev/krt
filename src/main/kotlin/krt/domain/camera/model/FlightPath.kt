package krt.domain.camera.model

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import krt.domain.scene.model.DemoScene
import krt.platform.math.Point3

/** Where the camera stands and the point it looks at, at one moment of a flight. */
data class FlightPose(val origin: Point3, val target: Point3)

/**
 * A closed camera flight: a Catmull-Rom spline through [keyframes], for both the position and the point looked at,
 * so the camera passes every keyframe smoothly and comes back to the first.
 */
class FlightPath(val keyframes: List<FlightPose>) {

    init {
        require(keyframes.size >= MIN_KEYFRAMES) { "a flight needs at least $MIN_KEYFRAMES keyframes" }
    }

    /** The pose at [step] of [steps] equal steps around the whole flight. */
    fun pose(step: Int, steps: Int): FlightPose {
        val position = step.toDouble() / steps * keyframes.size
        val segment = floor(position).toInt()
        val t = position - segment
        fun key(offset: Int) = keyframes[(segment + offset).mod(keyframes.size)]
        return FlightPose(
            origin = catmullRom(key(-1).origin, key(0).origin, key(1).origin, key(2).origin, t),
            target = catmullRom(key(-1).target, key(0).target, key(1).target, key(2).target, t),
        )
    }

    @Suppress("MagicNumber") // the coefficients of the Catmull-Rom spline
    private fun catmullRom(p0: Point3, p1: Point3, p2: Point3, p3: Point3, t: Double): Point3 {
        val t2 = t * t
        val t3 = t2 * t
        return (p1 * 2.0 + (p2 - p0) * t + (p0 * 2.0 - p1 * 5.0 + p2 * 4.0 - p3) * t2 +
            (p1 * 3.0 - p0 - p2 * 3.0 + p3) * t3) * 0.5
    }

    private companion object {
        const val MIN_KEYFRAMES = 3
    }
}

/** Fly-throughs of the demo scenes: orbits, dollies and low passes between their objects. */
@Suppress("MagicNumber") // the keyframes are the flights
object Flights {

    private val SHOWCASE = FlightPath(
        listOf(
            FlightPose(Point3(13.0, 2.0, 3.0), Point3(0.0, 1.0, 0.0)),
            FlightPose(Point3(8.0, 1.2, -6.0), Point3(0.0, 1.0, 0.0)),
            FlightPose(Point3(2.5, 1.0, -6.0), Point3(-1.0, 0.8, 0.0)),
            FlightPose(Point3(-4.0, 1.2, -3.5), Point3(0.0, 1.0, 0.0)),
            FlightPose(Point3(-6.5, 2.5, 3.0), Point3(0.0, 1.0, 0.0)),
            FlightPose(Point3(-1.5, 1.4, 6.0), Point3(1.0, 0.8, 0.0)),
            FlightPose(Point3(5.5, 3.5, 6.5), Point3(0.0, 1.0, 0.0)),
        ),
    )

    private val CLASSIC = FlightPath(
        listOf(
            FlightPose(Point3(13.0, 2.0, 3.0), Point3(0.0, 0.0, 0.0)),
            FlightPose(Point3(6.0, 1.5, 6.0), Point3(0.0, 1.0, 0.0)),
            FlightPose(Point3(-2.0, 1.4, 5.0), Point3(-4.0, 1.0, 0.0)),
            FlightPose(Point3(-9.0, 3.0, 2.0), Point3(0.0, 1.0, 0.0)),
            FlightPose(Point3(-8.0, 5.0, -8.0), Point3(0.0, 0.0, 0.0)),
            FlightPose(Point3(2.0, 2.5, -9.0), Point3(4.0, 1.0, 0.0)),
            FlightPose(Point3(11.0, 4.0, -4.0), Point3(0.0, 0.0, 0.0)),
        ),
    )

    /**
     * A swing in front of a set: from [origin] round the point looked at, [target], up to about 40° each way, closer
     * in and lower on the way, so walls behind the set stay out of the way.
     */
    private fun swing(origin: Point3, target: Point3): FlightPath {
        val offset = origin - target
        return FlightPath(
            SWING.map { (degrees, distance, lift) ->
                val angle = Math.toRadians(degrees)
                val (c, s) = cos(angle) to sin(angle)
                val turned = Point3(offset.x * c + offset.z * s, offset.y, -offset.x * s + offset.z * c)
                val origin = target + turned * distance + Point3(0.0, lift, 0.0)
                FlightPose(Point3(origin.x, maxOf(origin.y, MIN_SWING_HEIGHT), origin.z), target)
            },
        )
    }

    /** A swing never flies lower than this: close to a small set it would graze the table. */
    private const val MIN_SWING_HEIGHT = 0.7

    /** Angle in degrees, share of the start distance and lift of each keyframe of a [swing]. */
    private val SWING = listOf(
        Triple(0.0, 1.0, 0.0), Triple(25.0, 0.85, -0.2), Triple(40.0, 0.7, -0.3), Triple(15.0, 0.55, -0.4),
        Triple(-20.0, 0.7, -0.2), Triple(-40.0, 0.85, 0.1), Triple(-15.0, 1.05, 0.3),
    )

    private val PRISM = swing(Point3(2.0, 1.15, 1.95), Point3(0.0, 1.0, 0.0))
    private val BUBBLES = swing(Point3(0.6, 1.9, 5.2), Point3(0.0, 1.2, 0.0))
    private val CANDLE = swing(Point3(0.2, 1.0, 2.6), Point3(0.0, 0.5, 0.0))
    private val TEMPLE = swing(Point3(4.6, 2.3, 1.2), Point3(0.0, 1.4, 0.0))
    private val METALS = swing(Point3(0.2, 2.0, 5.6), Point3(0.0, 0.45, 0.0))

    fun of(scene: DemoScene): FlightPath = when (scene) {
        DemoScene.SHOWCASE, DemoScene.MATERIALS, DemoScene.FRACTALS -> SHOWCASE
        DemoScene.PRISM -> PRISM
        DemoScene.BUBBLES -> BUBBLES
        DemoScene.CANDLE -> CANDLE
        DemoScene.TEMPLE -> TEMPLE
        DemoScene.METALS -> METALS
        DemoScene.CLASSIC -> CLASSIC
    }
}
