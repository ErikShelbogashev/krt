package krt.platform.math

import kotlin.math.cos
import kotlin.math.sin

/** An affine transform of space as a 4×4 matrix, row by row; the last row is 0 0 0 1. */
class Matrix4(private val m: DoubleArray) {

    init {
        require(m.size == SIZE * SIZE)
    }

    operator fun get(row: Int, column: Int): Double = m[row * SIZE + column]

    operator fun times(other: Matrix4): Matrix4 = Matrix4(
        DoubleArray(SIZE * SIZE) { i ->
            val row = i / SIZE
            val column = i % SIZE
            (0 until SIZE).sumOf { this[row, it] * other[it, column] }
        },
    )

    fun point(p: Point3): Point3 = vector(p) + Point3(this[0, W], this[1, W], this[2, W])

    fun vector(v: Point3): Point3 = Point3(
        this[0, 0] * v.x + this[0, 1] * v.y + this[0, 2] * v.z,
        this[1, 0] * v.x + this[1, 1] * v.y + this[1, 2] * v.z,
        this[2, 0] * v.x + this[2, 1] * v.y + this[2, 2] * v.z,
    )

    /** A normal carried by this transform: by the inverse transpose of its 3×3 part, then made unit. */
    fun normal(n: Point3): Point3 {
        val a = this
        val cofactor = arrayOf(
            Point3(minor(a, 1, 1, 2, 2), minor(a, 1, 2, 2, 0), minor(a, 1, 0, 2, 1)),
            Point3(minor(a, 0, 2, 2, 1), minor(a, 0, 0, 2, 2), minor(a, 0, 1, 2, 0)),
            Point3(minor(a, 0, 1, 1, 2), minor(a, 0, 2, 1, 0), minor(a, 0, 0, 1, 1)),
        )
        // The cofactor matrix is the inverse transpose times the determinant, whose sign keeps the side.
        val sign = if (determinant() < 0) -1.0 else 1.0
        return Point3(cofactor[0].dot(n), cofactor[1].dot(n), cofactor[2].dot(n)).unit() * sign
    }

    /** a[r1, c1] a[r2, c2] − a[r1, c2] a[r2, c1]: a signed 2×2 minor, the cofactor when the indices go round. */
    @Suppress("LongParameterList")
    private fun minor(a: Matrix4, r1: Int, c1: Int, r2: Int, c2: Int): Double =
        a[r1, c1] * a[r2, c2] - a[r1, c2] * a[r2, c1]

    /** Determinant of the 3×3 part: below 0 when the transform mirrors space. */
    fun determinant(): Double =
        this[0, 0] * (this[1, 1] * this[2, 2] - this[1, 2] * this[2, 1]) -
            this[0, 1] * (this[1, 0] * this[2, 2] - this[1, 2] * this[2, 0]) +
            this[0, 2] * (this[1, 0] * this[2, 1] - this[1, 1] * this[2, 0])

    override fun equals(other: Any?): Boolean = other is Matrix4 && m.contentEquals(other.m)

    override fun hashCode(): Int = m.contentHashCode()

    companion object {
        private const val SIZE = 4

        /** The column of the translation. */
        private const val W = 3
        private const val DEGREES = 180.0

        val IDENTITY = scale(Point3(1.0, 1.0, 1.0))

        fun translate(d: Point3): Matrix4 =
            Matrix4(doubleArrayOf(1.0, 0.0, 0.0, d.x, 0.0, 1.0, 0.0, d.y, 0.0, 0.0, 1.0, d.z, 0.0, 0.0, 0.0, 1.0))

        fun scale(s: Point3): Matrix4 =
            Matrix4(doubleArrayOf(s.x, 0.0, 0.0, 0.0, 0.0, s.y, 0.0, 0.0, 0.0, 0.0, s.z, 0.0, 0.0, 0.0, 0.0, 1.0))

        /** Rotation by [degrees] around [axis], counter-clockwise looking down the axis, as pbrt's Rotate. */
        fun rotate(degrees: Double, axis: Point3): Matrix4 {
            val a = axis.unit()
            val theta = Math.PI * degrees / DEGREES
            val c = cos(theta)
            val s = sin(theta)
            return Matrix4(
                doubleArrayOf(
                    a.x * a.x + (1 - a.x * a.x) * c, a.x * a.y * (1 - c) - a.z * s, a.x * a.z * (1 - c) + a.y * s, 0.0,
                    a.x * a.y * (1 - c) + a.z * s, a.y * a.y + (1 - a.y * a.y) * c, a.y * a.z * (1 - c) - a.x * s, 0.0,
                    a.x * a.z * (1 - c) - a.y * s, a.y * a.z * (1 - c) + a.x * s, a.z * a.z + (1 - a.z * a.z) * c, 0.0,
                    0.0, 0.0, 0.0, 1.0,
                ),
            )
        }

        /** The matrix of 16 values given column by column, as pbrt's Transform and ConcatTransform take them. */
        fun ofColumns(values: List<Double>): Matrix4 =
            Matrix4(DoubleArray(SIZE * SIZE) { i -> values[(i % SIZE) * SIZE + i / SIZE] })
    }
}
