package krt.domain.scene.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import krt.platform.math.Point3

/** Where a mesh stands: its local axes [x], [y], [z] in the scene, around [center]. */
data class Pose(
    val center: Point3,
    val x: Point3 = Point3(1.0, 0.0, 0.0),
    val y: Point3 = Point3(0.0, 1.0, 0.0),
    val z: Point3 = Point3(0.0, 0.0, 1.0),
) {
    fun place(local: Point3): Point3 = center + x * local.x + y * local.y + z * local.z
}

/** Procedural meshes made of triangles: pure functions, so scenes need no model files. */
object Meshes {

    private const val TAU = 2 * PI
    private const val ICOSAHEDRON_SIDE = 1.618033988749895 // the golden ratio

    /** A flat rectangle, [halfWidth] along the pose's x and [halfHeight] along its y; two triangles. */
    fun panel(pose: Pose, halfWidth: Double, halfHeight: Double, material: Material): List<Triangle> {
        fun corner(u: Double, v: Double) = pose.place(Point3(u * halfWidth, v * halfHeight, 0.0))
        val lowLeft = corner(-1.0, -1.0)
        val highRight = corner(1.0, 1.0)
        return listOf(
            Triangle(lowLeft, corner(1.0, -1.0), highRight, material),
            Triangle(lowLeft, highRight, corner(-1.0, 1.0), material),
        )
    }

    /**
     * A closed box [half] its size each way along the pose's axes, its faces wound outwards; with [media] it bounds
     * them, like a block of wax (ADR 0014).
     */
    fun box(pose: Pose, half: Point3, material: Material, media: MediumInterface? = null): List<Triangle> {
        fun side(bit: Int, extent: Double) = if (bit == 0) -extent else extent
        fun corner(i: Int) =
            pose.place(Point3(side(i and X_BIT, half.x), side(i and Y_BIT, half.y), side(i and Z_BIT, half.z)))
        return BOX_FACES.flatMap { face ->
            val (a, b, c) = face.take(FACE_TRIANGLE).map(::corner)
            val d = corner(face.last())
            listOf(Triangle(a, b, c, material, media = media), Triangle(a, c, d, material, media = media))
        }
    }

    private const val X_BIT = 1
    private const val Y_BIT = 2
    private const val Z_BIT = 4
    private const val FACE_TRIANGLE = 3

    /** Corners of each face of a box, counter-clockwise seen from outside; bit 0 is x, bit 1 y, bit 2 z. */
    private val BOX_FACES = listOf(
        listOf(0, 2, 3, 1), listOf(4, 5, 7, 6), listOf(0, 1, 5, 4),
        listOf(2, 6, 7, 3), listOf(0, 4, 6, 2), listOf(1, 3, 7, 5),
    )

    /** A sphere of 20 × 4^[subdivisions] flat faces: a cut gem when coarse. */
    fun icosphere(pose: Pose, radius: Double, subdivisions: Int, material: Material): List<Triangle> {
        var faces = icosahedron()
        repeat(subdivisions) { faces = faces.flatMap(::split) }
        return faces.map { (a, b, c) ->
            Triangle(pose.place(a * radius), pose.place(b * radius), pose.place(c * radius), material)
        }
    }

    /** A ring around the pose's y axis: [radius] to the middle of a tube of [tube] radius. */
    @Suppress("LongParameterList")
    fun torus(pose: Pose, radius: Double, tube: Double, segments: Int, sides: Int, material: Material): List<Triangle> =
        tube(pose, segments, sides, tube, material) { t ->
            Point3(radius * cos(t), 0.0, radius * sin(t))
        }

    /**
     * A (p, q) torus knot: a tube along a curve that winds [p] times around the pose's y axis and [q] times
     * through the hole, [scale] its size and [tube] the radius of the tube.
     */
    @Suppress("LongParameterList")
    fun torusKnot(
        pose: Pose,
        scale: Double,
        tube: Double,
        p: Int,
        q: Int,
        segments: Int,
        sides: Int,
        material: Material,
    ): List<Triangle> = tube(pose, segments, sides, tube, material) { t ->
        val r = 2 + cos(q * t)
        Point3(r * cos(p * t), -sin(q * t), r * sin(p * t)) * scale
    }

    /**
     * A closed tube of [radius] along the closed curve [path] over t in [0, 2π): [segments] rings of [sides]
     * vertices, each ring across the curve, framed by the curve's tangent and its bend.
     */
    @Suppress("LongParameterList")
    private fun tube(
        pose: Pose,
        segments: Int,
        sides: Int,
        radius: Double,
        material: Material,
        path: (Double) -> Point3,
    ): List<Triangle> {
        val rings = List(segments) { i -> ring(path, TAU * i / segments, sides, radius) }
        return (0 until segments).flatMap { i ->
            val ring = rings[i]
            val next = rings[(i + 1) % segments]
            (0 until sides).flatMap { j ->
                val k = (j + 1) % sides
                listOf(
                    Triangle(pose.place(ring[j]), pose.place(next[j]), pose.place(next[k]), material),
                    Triangle(pose.place(ring[j]), pose.place(next[k]), pose.place(ring[k]), material),
                )
            }
        }
    }

    private fun ring(path: (Double) -> Point3, t: Double, sides: Int, radius: Double): List<Point3> {
        val step = 1e-3
        val point = path(t)
        val tangent = (path(t + step) - path(t - step)).unit()
        val bend = path(t + step) + path(t - step) - point * 2.0
        val binormal = tangent.cross(bend).unit()
        val normal = binormal.cross(tangent)
        return List(sides) { j ->
            val angle = TAU * j / sides
            point + (normal * cos(angle) + binormal * sin(angle)) * radius
        }
    }

    private fun icosahedron(): List<Triple<Point3, Point3, Point3>> {
        val g = ICOSAHEDRON_SIDE
        val v = listOf(
            Point3(-1.0, g, 0.0), Point3(1.0, g, 0.0), Point3(-1.0, -g, 0.0), Point3(1.0, -g, 0.0),
            Point3(0.0, -1.0, g), Point3(0.0, 1.0, g), Point3(0.0, -1.0, -g), Point3(0.0, 1.0, -g),
            Point3(g, 0.0, -1.0), Point3(g, 0.0, 1.0), Point3(-g, 0.0, -1.0), Point3(-g, 0.0, 1.0),
        ).map { it.unit() }
        return ICOSAHEDRON_FACES.map { (a, b, c) -> Triple(v[a], v[b], v[c]) }
    }

    /** Four faces from one: the edge midpoints pushed out onto the unit sphere. */
    private fun split(face: Triple<Point3, Point3, Point3>): List<Triple<Point3, Point3, Point3>> {
        val (a, b, c) = face
        val ab = ((a + b) * 0.5).unit()
        val bc = ((b + c) * 0.5).unit()
        val ca = ((c + a) * 0.5).unit()
        return listOf(Triple(a, ab, ca), Triple(b, bc, ab), Triple(c, ca, bc), Triple(ab, bc, ca))
    }

    /**
     * The same triangles shading as one smooth surface: each corner gets the mean normal of the faces that share
     * that point, weighted by their area. Faces facing away from the mean at a corner keep a crease there.
     */
    fun smooth(triangles: List<Triangle>): List<Triangle> {
        val sums = HashMap<Point3, Point3>()
        for (triangle in triangles) {
            val weighted = (triangle.v1 - triangle.v0).cross(triangle.v2 - triangle.v0)
            for (corner in listOf(triangle.v0, triangle.v1, triangle.v2)) {
                sums[corner] = (sums[corner] ?: Point3.ZERO) + weighted
            }
        }
        return triangles.map { triangle ->
            val flat = (triangle.v1 - triangle.v0).cross(triangle.v2 - triangle.v0).unit()
            fun normalAt(corner: Point3) = sums.getValue(corner).unit().takeIf { it.dot(flat) > CREASE } ?: flat
            Triangle(
                triangle.v0, triangle.v1, triangle.v2, triangle.material, triangle.id,
                normals = VertexNormals(normalAt(triangle.v0), normalAt(triangle.v1), normalAt(triangle.v2)),
            )
        }
    }

    /** A corner whose mean normal turns further than this cosine from the face keeps the face's own normal. */
    private const val CREASE = 0.5

    private val ICOSAHEDRON_FACES = listOf(
        Triple(0, 11, 5), Triple(0, 5, 1), Triple(0, 1, 7), Triple(0, 7, 10), Triple(0, 10, 11),
        Triple(1, 5, 9), Triple(5, 11, 4), Triple(11, 10, 2), Triple(10, 7, 6), Triple(7, 1, 8),
        Triple(3, 9, 4), Triple(3, 4, 2), Triple(3, 2, 6), Triple(3, 6, 8), Triple(3, 8, 9),
        Triple(4, 9, 5), Triple(2, 4, 11), Triple(6, 2, 10), Triple(8, 6, 7), Triple(9, 8, 1),
    )
}
