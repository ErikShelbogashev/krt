package krt.platform.math

import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

data class Point3(val x: Double = 0.0, val y: Double = 0.0, val z: Double = 0.0) {
    constructor(x: Number, y: Number, z: Number) : this(x.toDouble(), y.toDouble(), z.toDouble())

    companion object {
        val ZERO = Point3()
        val ONE = Point3(1.0, 1.0, 1.0)

        fun random(random: Random, from: Double = 0.0, until: Double = 1.0): Point3 =
            Point3(random.nextDouble(from, until), random.nextDouble(from, until), random.nextDouble(from, until))

        fun randomInUnitSphere(random: Random): Point3 {
            while (true) {
                val v = random(random, 0.0, 1.0)
                if (v.length() < 1) return v
            }
        }

        fun randomInUnitDisk(random: Random): Point3 {
            while (true) {
                val p = Point3(random.nextDouble(0.0, 1.0), random.nextDouble(0.0, 1.0), 0.0)
                if (p.length() < 1) return p
            }
        }
    }

    operator fun unaryMinus(): Point3 = Point3(-x, -y, -z)
    operator fun get(i: Int): Double = when (i) {
        0 -> x
        1 -> y
        2 -> z
        else -> throw IndexOutOfBoundsException()
    }

    operator fun plus(other: Point3): Point3 = Point3(x + other.x, y + other.y, z + other.z)
    operator fun plus(scalar: Double): Point3 = Point3(x + scalar, y + scalar, z + scalar)
    operator fun minus(other: Point3): Point3 = Point3(x - other.x, y - other.y, z - other.z)
    operator fun minus(scalar: Double): Point3 = Point3(x - scalar, y - scalar, z - scalar)
    operator fun times(other: Point3): Point3 = Point3(x * other.x, y * other.y, z * other.z)
    operator fun times(scalar: Double): Point3 = Point3(x * scalar, y * scalar, z * scalar)
    operator fun div(other: Point3): Point3 = Point3(x / other.x, y / other.y, z / other.z)
    operator fun div(scalar: Double): Point3 = Point3(x / scalar, y / scalar, z / scalar)
    private fun l2norm(): Double = x * x + y * y + z * z
    fun length(): Double = sqrt(l2norm())
    fun unit(): Point3 = this / length()
    fun dot(other: Point3): Double = x * other.x + y * other.y + z * other.z
    fun cross(other: Point3): Point3 =
        Point3(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x)

    fun reflect(normal: Point3): Point3 = this - normal * 2.0 * this.dot(normal)

    fun refract(normal: Point3, etaiOverEtat: Double): Point3 {
        val cosTheta = min((-this).dot(normal), 1.0)
        val rOutPerp = (this + normal * cosTheta) * etaiOverEtat
        val rOutParallel = normal * (-sqrt((1.0 - rOutPerp.l2norm()).absoluteValue))
        return rOutPerp + rOutParallel
    }

    fun isNearZero(): Boolean = (x * x + y * y + z * z) < 1e-8
}

typealias Color = Point3
