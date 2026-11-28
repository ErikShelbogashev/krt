package krt.domain.scene.model

import java.util.UUID
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import krt.platform.math.Aabb
import krt.platform.math.Point3
import krt.platform.math.Ray
import krt.platform.math.SphereRoots

/** The fractals krt draws by their distance estimates (ADR 0015), each with the radius it fits in natively. */
@Suppress("MagicNumber") // the shapes' own sizes and defaults
enum class FractalKind(
    val nativeRadius: Double,
    val iterations: Int,
    val parameter: Double,
    val gpuIterations: Int = 16,
) {
    /** The 3D Mandelbrot set of White and Nylander; [Fractal.parameter] is its power. */
    MANDELBULB(1.25, 10, 8.0),

    /** Menger's sponge: a cube with holes, holes in the holes, [Fractal.iterations] deep. */
    MENGER(1.75, 4, 0.0, 8),

    /** Lowe's Mandelbox; [Fractal.parameter] is its scale, 2 for the classic box. */
    MANDELBOX(10.4, 12, 2.0),

    /** A slice through a quaternion Julia set; [Fractal.seed] is its constant c (with w = 0). */
    JULIA(1.5, 11, 0.0),
}

/**
 * A fractal of [kind] scaled to fit a ball of [radius] around [center], drawn by sphere tracing its distance
 * estimate (ADR 0015): a march along the ray by the distance to the surface, until it is closer than a ten-thousandth
 * of the radius. The shader marches the same way.
 */
class Fractal(
    val kind: FractalKind,
    val center: Point3,
    val radius: Double,
    val material: Material,
    override val id: String = "Fractal[${UUID.randomUUID()}]",
    val iterations: Int = kind.iterations,
    val parameter: Double = kind.parameter,
    val seed: Point3 = JULIA_SEED,
) : Traceable {

    /** World units per unit of the fractal's own space. */
    private val scale = radius / kind.nativeRadius

    /** Distance from [p] in the world to the surface, at most: never past it. */
    fun distance(p: Point3): Double {
        val q = (p - center) / scale
        return FractalDistance.of(kind, q.x, q.y, q.z, iterations, parameter, seed) * scale
    }

    override fun hit(ray: Ray, tMin: Double, tMax: Double): Hit? {
        val a = ray.direction.dot(ray.direction)
        return if (a in java.lang.Double.MIN_NORMAL..Double.MAX_VALUE) march(ray, tMin, tMax, a)
            else Traceable.withScaledDirection(this, ray, tMin, tMax)
    }

    private fun march(ray: Ray, tMin: Double, tMax: Double, a: Double): Hit? {
        val oc = ray.origin - center
        val halfB = oc.dot(ray.direction)
        val discriminant = SphereRoots.discriminant(oc, ray.direction, a, halfB, radius)
        if (!(discriminant >= 0.0) || !discriminant.isFinite()) return null
        val root = sqrt(discriminant)
        val speed = sqrt(a)
        var t = maxOf((-halfB - root) / a, tMin)
        val leave = minOf((-halfB + root) / a, tMax)
        val epsilon = radius * SURFACE
        var found = false
        var step = 0
        // On scalars, the march allocates nothing per step.
        val (ox, oy, oz) = oc
        val (dx, dy, dz) = ray.direction
        while (!found && t < leave && step < MAX_STEPS) {
            val d = FractalDistance.of(
                kind, (ox + dx * t) / scale, (oy + dy * t) / scale, (oz + dz * t) / scale, iterations, parameter, seed,
            ) * scale
            found = d < epsilon
            if (!found) t += d / speed
            step++
        }
        return if (found) surfaceAt(ray, t) else null
    }

    private fun surfaceAt(ray: Ray, t: Double): Hit {
        val p = ray.at(t)
        return Hit.create(p, t, ray, normalAt(p), material, lightSampled = false)
    }

    /** The normal at [p]: the gradient of the distance, by four taps on a tetrahedron. */
    fun normalAt(p: Point3): Point3 {
        val h = radius * NORMAL_STEP
        val a = Point3(1.0, -1.0, -1.0)
        val b = Point3(-1.0, -1.0, 1.0)
        val c = Point3(-1.0, 1.0, -1.0)
        val d = Point3(1.0, 1.0, 1.0)
        val gradient = a * distance(p + a * h) + b * distance(p + b * h) + c * distance(p + c * h) +
            d * distance(p + d * h)
        return gradient.unit()
    }

    override fun bounds(): Aabb = Aabb(center - Point3(radius, radius, radius), center + Point3(radius, radius, radius))

    override fun translate(offset: Point3): Fractal =
        Fractal(kind, center + offset, radius, material, id, iterations, parameter, seed)

    override fun linearize(coefficient: Double): List<LineSegment> =
        Sphere(center, radius, material).linearize(coefficient)

    companion object {
        /** A Julia set constant that gives a rounded, lobed shape. */
        val JULIA_SEED = Point3(-0.2, 0.6, 0.2)

        /** Steps of the march at most, as many as the shader takes. */
        const val MAX_STEPS = 160

        /** Closer than this share of the radius counts as on the surface. */
        const val SURFACE = 1e-4

        /** Step of the normal's taps, as a share of the radius. */
        const val NORMAL_STEP = 2e-4
    }
}

/** Distance estimates of the fractals in their own space: pure functions, the same as the shader's. */
object FractalDistance {
    private const val BAILOUT = 2.0
    private const val JULIA_ESCAPE = 4.0
    private const val MENGER_CELL = 3.0
    private const val FOLD_LIMIT = 1.0
    private const val MIN_RADIUS2 = 0.25
    private const val FIXED_RADIUS2 = 1.0

    @Suppress("LongParameterList")
    fun of(
        kind: FractalKind,
        x: Double,
        y: Double,
        z: Double,
        iterations: Int,
        parameter: Double,
        seed: Point3,
    ): Double =
        when (kind) {
            FractalKind.MANDELBULB -> mandelbulb(x, y, z, iterations, parameter)
            FractalKind.MENGER -> menger(x, y, z, iterations)
            FractalKind.MANDELBOX -> mandelbox(x, y, z, iterations, parameter)
            FractalKind.JULIA -> julia(x, y, z, iterations, seed)
        }

    fun mandelbulb(px: Double, py: Double, pz: Double, iterations: Int, power: Double): Double {
        var x = px
        var y = py
        var z = pz
        var dr = 1.0
        var r = sqrt(x * x + y * y + z * z)
        var i = 0
        while (i < iterations && r <= BAILOUT) {
            if (r == 0.0) return 0.0 // The critical orbit returned to its origin and repeats.
            val theta = acos((z / r).coerceIn(-1.0, 1.0)) * power
            val phi = atan2(y, x) * power
            dr = power * r.pow(power - 1) * dr + 1
            val zr = r.pow(power)
            val sinTheta = sin(theta)
            x = zr * sinTheta * cos(phi) + px
            y = zr * sinTheta * sin(phi) + py
            z = zr * cos(theta) + pz
            r = sqrt(x * x + y * y + z * z)
            i++
        }
        return if (r == 0.0) 0.0 else HALF * ln(r) * r / dr
    }

    fun menger(px: Double, py: Double, pz: Double, iterations: Int): Double {
        var d = box(px, py, pz)
        var s = 1.0
        repeat(iterations) {
            val ax = wrap(px * s) - 1
            val ay = wrap(py * s) - 1
            val az = wrap(pz * s) - 1
            s *= MENGER_CELL
            val rx = abs(1 - MENGER_CELL * abs(ax))
            val ry = abs(1 - MENGER_CELL * abs(ay))
            val rz = abs(1 - MENGER_CELL * abs(az))
            val c = (min(max(rx, ry), min(max(ry, rz), max(rz, rx))) - 1) / s
            d = max(d, c)
        }
        return d
    }

    fun mandelbox(px: Double, py: Double, pz: Double, iterations: Int, scale: Double): Double {
        var x = px
        var y = py
        var z = pz
        var dr = 1.0
        repeat(iterations) {
            x = fold(x)
            y = fold(y)
            z = fold(z)
            val r2 = x * x + y * y + z * z
            val factor = when {
                r2 < MIN_RADIUS2 -> FIXED_RADIUS2 / MIN_RADIUS2
                r2 < FIXED_RADIUS2 -> FIXED_RADIUS2 / r2
                else -> 1.0
            }
            x = x * factor * scale + px
            y = y * factor * scale + py
            z = z * factor * scale + pz
            dr = dr * factor * abs(scale) + 1
        }
        return sqrt(x * x + y * y + z * z) / abs(dr)
    }

    fun julia(px: Double, py: Double, pz: Double, iterations: Int, c: Point3): Double {
        // For c = 0 the quaternion norm squares each iteration; its filled Julia set is the unit ball.
        if (c == Point3.ZERO) return sqrt(px * px + py * py + pz * pz) - 1.0
        var qx = px
        var qy = py
        var qz = pz
        var qw = 0.0
        var md2 = 1.0
        var mz2 = qx * qx + qy * qy + qz * qz
        var i = 0
        while (i < iterations && mz2 <= JULIA_ESCAPE) {
            md2 *= JULIA_ESCAPE * mz2
            val nx = qx * qx - qy * qy - qz * qz - qw * qw + c.x
            val ny = 2 * qx * qy + c.y
            val nz = 2 * qx * qz + c.z
            val nw = 2 * qx * qw
            qx = nx
            qy = ny
            qz = nz
            qw = nw
            mz2 = qx * qx + qy * qy + qz * qz + qw * qw
            i++
        }
        return QUARTER * sqrt(mz2 / md2) * ln(mz2)
    }

    private fun box(x: Double, y: Double, z: Double): Double {
        val qx = abs(x) - 1
        val qy = abs(y) - 1
        val qz = abs(z) - 1
        val outside = sqrt(max(qx, 0.0).let { it * it } + max(qy, 0.0).let { it * it } + max(qz, 0.0).let { it * it })
        return outside + min(max(qx, max(qy, qz)), 0.0)
    }

    /** x mod 2, in [0, 2), as GLSL's mod. */
    private fun wrap(x: Double): Double = x - 2 * floor(x / 2)

    private fun fold(x: Double): Double = x.coerceIn(-FOLD_LIMIT, FOLD_LIMIT) * 2 - x

    private const val HALF = 0.5
    private const val QUARTER = 0.25
}
