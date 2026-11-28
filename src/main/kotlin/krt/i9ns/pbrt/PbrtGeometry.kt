package krt.i9ns.pbrt

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.Fog
import krt.domain.scene.model.Fractal
import krt.domain.scene.model.FractalKind
import krt.domain.scene.model.Interface
import krt.domain.scene.model.Material
import krt.domain.scene.model.MediumInterface
import krt.domain.scene.model.Sphere
import krt.domain.scene.model.Traceable
import krt.domain.scene.model.Triangle
import krt.domain.scene.model.VertexNormals
import krt.platform.math.Matrix4
import krt.platform.math.Point3

/**
 * Where a shape of a scene file stands: its transforms at the start ([ctm]) and end ([ctmEnd]) of the shutter, the
 * [motion] a translation at the end alone gives it, and whether its orientation is reversed.
 */
data class Placement(
    val ctm: Matrix4 = Matrix4.IDENTITY,
    val ctmEnd: Matrix4 = Matrix4.IDENTITY,
    val motion: Point3 = Point3.ZERO,
    val reverse: Boolean = false,
) {
    val origin: Point3 get() = ctm.point(Point3.ZERO)

    /** How much the transform scales lengths, along its x axis: shapes are taken as scaled evenly. */
    val scale: Double get() = ctm.vector(Point3(1.0, 0.0, 0.0)).length()
}

/** What covers a shape: its [material], the [media] it bounds, and the [name] the file gives it. */
data class Covering(val material: Material, val media: MediumInterface?, val name: String?)

/** Shapes of pbrt-v4 as krt objects (ADR 0016): pure transformations. */
object PbrtGeometry {
    private const val POINT_VALUES = 3
    private const val QUAD = 4
    private const val LAST_CORNER = 3

    fun sphere(d: PbrtDirective, placement: Placement, covering: Covering, number: Int): Traceable {
        val center = placement.origin
        val radius = d.double("radius", 1.0) * placement.scale
        val velocity = placement.motion.takeIf { it != Point3.ZERO } ?: (placement.ctmEnd.point(Point3.ZERO) - center)
        val id = covering.name ?: "Sphere[$number]"
        val inside = covering.media?.inside
        return if (covering.material === Interface && inside != null) {
            Fog(center, radius, inside, id, covering.media.outside)
        } else {
            val material = covering.material
            val oriented = if (placement.reverse && material is Emissive) material.reversed() else material
            Sphere(center, radius, oriented, id, velocity, covering.media)
        }
    }

    fun fractal(
        d: PbrtDirective,
        placement: Placement,
        covering: Covering,
        number: Int,
        warnings: MutableList<String>,
    ): Traceable {
        val kind = FractalKind.valueOf(d.text("kind", "mandelbulb").uppercase())
        val iterations = d.int("iterations", kind.iterations)
        if (iterations !in kind.iterationRange) {
            val effective = iterations.coerceIn(kind.iterationRange)
            warnings += "line ${d.line}: $kind requested $iterations iterations, CPU and GPU use $effective"
        }
        return Fractal(
            kind,
            placement.origin,
            d.double("radius", 1.0) * placement.scale,
            covering.material,
            covering.name ?: "Fractal[$number]",
            iterations,
            d.double("parameter", kind.parameter),
            PbrtValues.vector(d, "seed") ?: Fractal.JULIA_SEED,
        )
    }

    fun meshOf(d: PbrtDirective): PlyMesh {
        val points = d.params["P"]?.doubles()?.chunked(POINT_VALUES)?.map { Point3(it[0], it[1], it[2]) }
            ?: d.fail("P expected")
        val indices = d.params["indices"]?.doubles()?.map { it.toInt() } ?: points.indices.toList()
        val normals = d.params["N"]?.doubles()?.chunked(POINT_VALUES)?.map { Point3(it[0], it[1], it[2]) }
        return PlyMesh(points, normals, indices.chunked(POINT_VALUES).map { it.toIntArray() })
    }

    fun bilinear(d: PbrtDirective): PlyMesh {
        val quads = meshOf(d)
        val corners = quads.triangles.flatMap { it.toList() }.chunked(QUAD).flatMap { quad ->
            listOf(intArrayOf(quad[0], quad[1], quad[LAST_CORNER]), intArrayOf(quad[0], quad[LAST_CORNER], quad[2]))
        }
        return PlyMesh(quads.points, quads.normals, corners)
    }

    /** The triangles of [mesh] placed by [placement]; reversed orientation or a mirroring transform flips them. */
    fun mesh(d: PbrtDirective, mesh: PlyMesh, placement: Placement, covering: Covering): List<Triangle> {
        val ctm = placement.ctm
        val flip = placement.reverse xor (ctm.determinant() < 0)
        val placed = ctm != Matrix4.IDENTITY
        val points = if (placed) mesh.points.map(ctm::point) else mesh.points
        val normals = if (placed) mesh.normals?.map(ctm::normal) else mesh.normals
        if (mesh.triangles.any { t -> t.any { it !in points.indices } }) d.fail("index out of range")
        return mesh.triangles.map { t ->
            val (a, b, c) = if (flip) Triple(t[0], t[2], t[1]) else Triple(t[0], t[1], t[2])
            val smooth = normals?.let { n -> VertexNormals(n[a], n[b], n[c]) }
            Triangle(points[a], points[b], points[c], covering.material, covering.name!!, smooth, covering.media)
        }
    }
}

/** Shapes of pbrt-v4 that krt draws as triangles: pure tessellations around the z axis. */
object PbrtShapes {
    private const val SEGMENTS = 48

    fun disk(radius: Double, height: Double): PlyMesh {
        val ring = (0 until SEGMENTS).map { angle(it).let { (c, s) -> Point3(radius * c, radius * s, height) } }
        val center = SEGMENTS
        val fan = (0 until SEGMENTS).map { intArrayOf(center, it, (it + 1) % SEGMENTS) }
        return PlyMesh(ring + Point3(0.0, 0.0, height), null, fan)
    }

    fun cylinder(d: PbrtDirective): PlyMesh {
        val radius = d.double("radius", 1.0)
        val zMin = d.double("zmin", -1.0)
        val zMax = d.double("zmax", 1.0)
        val points = (0 until SEGMENTS).flatMap { i ->
            val (c, s) = angle(i)
            listOf(Point3(radius * c, radius * s, zMin), Point3(radius * c, radius * s, zMax))
        }
        val normals = (0 until SEGMENTS).flatMap { i -> angle(i).let { (c, s) -> List(2) { Point3(c, s, 0.0) } } }
        val triangles = (0 until SEGMENTS).flatMap { i ->
            val j = (i + 1) % SEGMENTS
            listOf(intArrayOf(2 * i, 2 * j, 2 * j + 1), intArrayOf(2 * i, 2 * j + 1, 2 * i + 1))
        }
        return PlyMesh(points, normals, triangles)
    }

    private fun angle(i: Int): Pair<Double, Double> = (2 * PI * i / SEGMENTS).let { cos(it) to sin(it) }
}
