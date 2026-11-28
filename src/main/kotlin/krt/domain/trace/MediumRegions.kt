package krt.domain.trace

import krt.domain.scene.model.ObjectOptics
import krt.domain.scene.model.SceneObjectEdits
import krt.domain.scene.model.Sphere
import krt.domain.scene.model.Traceable
import krt.domain.scene.model.Triangle
import krt.platform.math.Aabb
import krt.platform.math.Point3
import krt.platform.math.Ray

/** Enclosing boundaries indexed by primitive, since one editable object may have several closed surfaces. */
internal class MediumRegions(objects: List<Traceable>) {
    private val owners = arrayOfNulls<String>(objects.size)

    init {
        val boundaries = objects.indices.filter { index ->
            ObjectOptics.inside(objects[index]) != null || SceneObjectEdits.outside(objects[index]) != null
        }
        val surfaces = closedSurfaces(objects, boundaries)
        if (surfaces.isNotEmpty()) {
            val tree = RegionTree(surfaces)
            for (surface in surfaces) {
                for (index in surface.indices) {
                    val part = objects[index]
                    if (SceneObjectEdits.outside(part) != null) {
                        owners[index] = tree.owner(Ray(surfacePoint(part), DIRECTION), surface)
                    }
                }
            }
        }
    }

    fun ownerOf(index: Int): String? = owners[index]

    private fun surfacePoint(part: Traceable): Point3 = when (part) {
        is Triangle -> (part.v0 + part.v1 + part.v2) / TRIANGLE_CORNERS
        is Sphere -> part.center + Point3(part.radius, 0, 0)
        else -> part.bounds().let { Point3(it.max.x, it.center.y, it.center.z) }
    }

    /** Components within a named mesh connect along edges; touching at a vertex keeps the shells distinct. */
    private fun closedSurfaces(objects: List<Traceable>, boundaries: List<Int>): List<RegionSurface> {
        val mesh = boundaries.filter { objects[it] is Triangle }.groupBy { objects[it].id }
        val shapes = boundaries.filter { objects[it] !is Triangle }.map { listOf(it) }
        val shells = mesh.values.flatMap { indices ->
            val groups = EdgeGroups(indices.size)
            indices.forEachIndexed { local, index ->
                val triangle = objects[index] as Triangle
                // Mirroring can produce −0.0 at a duplicated vertex. +0.0 maps both signs to one edge key.
                val a = triangle.v0 + 0.0
                val b = triangle.v1 + 0.0
                val c = triangle.v2 + 0.0
                groups.join(local, a, b)
                groups.join(local, b, c)
                groups.join(local, c, a)
            }
            indices.indices.groupBy(groups::root).values.map { face -> face.map(indices::get) }
        }
        return (shapes + shells).map { RegionSurface(it, objects) }
    }

    private companion object {
        const val TRIANGLE_CORNERS = 3.0
        val DIRECTION = Point3(1.0, kotlin.math.sqrt(2.0), kotlin.math.sqrt(3.0))
    }
}

private class EdgeGroups(size: Int) {
    private val parents = IntArray(size) { it }
    private val edges = HashMap<Pair<Point3, Point3>, Int>()

    fun root(index: Int): Int {
        var current = index
        while (parents[current] != current) {
            parents[current] = parents[parents[current]]
            current = parents[current]
        }
        return current
    }

    fun join(index: Int, a: Point3, b: Point3) {
        val previous = edges[b to a] ?: edges.putIfAbsent(a to b, index) ?: return
        parents[root(index)] = root(previous)
    }
}

private class RegionSurface(val indices: List<Int>, objects: List<Traceable>) {
    private val first = objects[indices.first()]
    val id = first.id
    val bounds = indices.map { objects[it].bounds() }.reduce(::enclose)
    private val mesh = if (indices.size > 1) Bvh(indices.map(objects::get).toTypedArray()) else null

    fun exit(ray: Ray, far: Double): Double? {
        val firstHit = if (mesh != null) mesh.hit(ray, Double.MIN_VALUE, far) else first.hit(ray, Double.MIN_VALUE, far)
        return firstHit?.takeUnless { it.isFrontFace }?.t
    }
}

/** Broad phase over complete closed surfaces. Only volumes whose bounding box contains the point can enclose it. */
private class RegionTree(surfaces: List<RegionSurface>) {
    private val bounds = surfaces.map { it.bounds }.reduce(::enclose)
    private val leaf = surfaces.singleOrNull()
    private val children = if (leaf != null) emptyList() else {
        val size = bounds.max - bounds.min
        val axis = (0..2).maxBy { size[it] }
        val ordered = surfaces.sortedBy { it.bounds.center[axis] }
        val middle = ordered.size / 2
        listOf(RegionTree(ordered.subList(0, middle)), RegionTree(ordered.subList(middle, ordered.size)))
    }

    fun owner(ray: Ray, source: RegionSurface): String? = Closest().also { visit(ray, source, it) }.id

    private fun visit(ray: Ray, source: RegionSurface, best: Closest) {
        if (leaf === source || !contains(bounds, ray.origin)) return
        if (leaf == null) {
            for (child in children) child.visit(ray, source, best)
        } else {
            val distance = leaf.exit(ray, best.distance) ?: return
            best.distance = distance
            best.id = leaf.id
        }
    }

    private class Closest(var distance: Double = Double.POSITIVE_INFINITY, var id: String? = null)
}

private fun contains(box: Aabb, point: Point3): Boolean =
    point.x in box.min.x..box.max.x && point.y in box.min.y..box.max.y && point.z in box.min.z..box.max.z

private fun enclose(a: Aabb, b: Aabb): Aabb = Aabb(
    Point3(minOf(a.min.x, b.min.x), minOf(a.min.y, b.min.y), minOf(a.min.z, b.min.z)),
    Point3(maxOf(a.max.x, b.max.x), maxOf(a.max.y, b.max.y), maxOf(a.max.z, b.max.z)),
)
