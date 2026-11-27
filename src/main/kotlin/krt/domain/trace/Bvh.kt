package krt.domain.trace

import krt.domain.scene.model.Hit
import krt.domain.scene.model.Traceable
import krt.platform.math.Ray

/**
 * Bounding volume hierarchy over the objects of a scene (ADR 0008).
 *
 * Nodes live in flat primitive arrays: node `n` has its box at `boxes[n * 6 until n * 6 + 6]`
 * (min x, y, z, then max x, y, z); an inner node has children `left[n]` and `right[n]`,
 * a leaf has `left[n] == LEAF` and holds objects `order[first[n] until first[n] + count[n]]`.
 * Built once, read-only afterwards.
 */
internal class Bvh(private val objects: Array<Traceable>) {

    private val order = IntArray(objects.size) { it }
    private val objectBoxes = DoubleArray(objects.size * BOX_SIZE).also { boxes ->
        objects.forEachIndexed { i, obj ->
            val bounds = obj.bounds()
            for (axis in 0 until AXES) {
                boxes[i * BOX_SIZE + axis] = bounds.min[axis]
                boxes[i * BOX_SIZE + AXES + axis] = bounds.max[axis]
            }
        }
    }

    private val maxNodes = maxOf(1, 2 * objects.size)
    private val boxes = DoubleArray(maxNodes * BOX_SIZE)
    private val left = IntArray(maxNodes)
    private val right = IntArray(maxNodes)
    private val first = IntArray(maxNodes)
    private val count = IntArray(maxNodes)
    private var nodeCount = 0

    init {
        if (objects.isNotEmpty()) build(0, objects.size)
    }

    /**
     * The hit with the smallest t, the same one a scan of all objects in order would return:
     * on equal t the object that comes first in the scene wins.
     */
    fun hit(ray: Ray, tMin: Double, tMax: Double): Hit? {
        if (objects.isEmpty()) return null
        val search = Search(ray, tMin, tMax)
        val stack = IntArray(STACK_DEPTH)
        var top = 0
        stack[top++] = ROOT
        while (top > 0) {
            val node = stack[--top]
            if (!search.entersBox(boxes, node)) continue
            if (left[node] == LEAF) {
                for (k in first[node] until first[node] + count[node]) search.test(objects, order[k])
            } else {
                stack[top++] = right[node]
                stack[top++] = left[node]
            }
        }
        return search.closest
    }

    /**
     * The tree for a walk without a stack (ADR 0009). Nodes are numbered depth-first, so the walk goes from a node
     * whose box the ray enters to the next number, its left child or, after a leaf, the next subtree; from a node it
     * misses, to its skip link, the first node after its subtree.
     */
    fun flatten(): FlatBvh {
        val skip = IntArray(nodeCount)
        if (nodeCount > 0) link(skip, ROOT, nodeCount)
        return FlatBvh(
            order = order.copyOf(),
            boxes = boxes.copyOf(nodeCount * BOX_SIZE),
            skip = skip,
            first = first.copyOf(nodeCount),
            count = IntArray(nodeCount) { if (left[it] == LEAF) count[it] else 0 },
        )
    }

    private fun link(skip: IntArray, node: Int, next: Int) {
        skip[node] = next
        if (left[node] == LEAF) return
        link(skip, left[node], right[node])
        link(skip, right[node], next)
    }

    private fun build(start: Int, end: Int): Int {
        val node = nodeCount++
        enclose(node, start, end)
        if (end - start <= LEAF_SIZE) {
            left[node] = LEAF
            first[node] = start
            count[node] = end - start
            return node
        }
        sortByCentre(start, end, longestAxis(node))
        val middle = (start + end) / 2
        left[node] = build(start, middle)
        right[node] = build(middle, end)
        return node
    }

    private fun enclose(node: Int, start: Int, end: Int) {
        val at = node * BOX_SIZE
        for (axis in 0 until AXES) {
            boxes[at + axis] = Double.POSITIVE_INFINITY
            boxes[at + AXES + axis] = Double.NEGATIVE_INFINITY
        }
        for (k in start until end) {
            val obj = order[k] * BOX_SIZE
            for (axis in 0 until AXES) {
                boxes[at + axis] = minOf(boxes[at + axis], objectBoxes[obj + axis])
                boxes[at + AXES + axis] = maxOf(boxes[at + AXES + axis], objectBoxes[obj + AXES + axis])
            }
        }
    }

    private fun longestAxis(node: Int): Int {
        val at = node * BOX_SIZE
        var best = 0
        for (axis in 1 until AXES) {
            val extent = boxes[at + AXES + axis] - boxes[at + axis]
            if (extent > boxes[at + AXES + best] - boxes[at + best]) best = axis
        }
        return best
    }

    /** Stable sort keeps the build deterministic for objects with the same centre. */
    private fun sortByCentre(start: Int, end: Int, axis: Int) {
        val sorted = order.copyOfRange(start, end)
            .sortedBy { objectBoxes[it * BOX_SIZE + axis] + objectBoxes[it * BOX_SIZE + AXES + axis] }
        sorted.forEachIndexed { i, obj -> order[start + i] = obj }
    }

    /** State of one closest-hit search; local to a [hit] call. */
    private class Search(private val ray: Ray, private val tMin: Double, private val tMax: Double) {
        var closest: Hit? = null
            private set
        private var closestIndex = Int.MAX_VALUE

        private val origin = doubleArrayOf(ray.origin.x, ray.origin.y, ray.origin.z)
        private val inverseDirection =
            doubleArrayOf(1.0 / ray.direction.x, 1.0 / ray.direction.y, 1.0 / ray.direction.z)

        private val far: Double get() = closest?.t ?: tMax

        fun test(objects: Array<Traceable>, index: Int) {
            val hit = objects[index].hit(ray, tMin, far) ?: return
            if (isCloser(hit, index)) {
                closest = hit
                closestIndex = index
            }
        }

        private fun isCloser(hit: Hit, index: Int): Boolean {
            val current = closest ?: return true
            return hit.t < current.t || (hit.t == current.t && index < closestIndex)
        }

        /**
         * Slab test against node [node]'s box within [tMin, far]. A box touched exactly at `far`
         * still counts, so ties are not lost; NaN from a zero direction keeps the box, never drops it.
         */
        fun entersBox(boxes: DoubleArray, node: Int): Boolean {
            val at = node * BOX_SIZE
            var near = tMin
            var exit = far
            for (axis in 0 until AXES) {
                val t1 = (boxes[at + axis] - origin[axis]) * inverseDirection[axis]
                val t2 = (boxes[at + AXES + axis] - origin[axis]) * inverseDirection[axis]
                near = maxOrSame(near, minOf(t1, t2))
                exit = minOrSame(exit, maxOf(t1, t2))
            }
            return exit >= near
        }

        private fun maxOrSame(current: Double, candidate: Double) = if (candidate > current) candidate else current

        private fun minOrSame(current: Double, candidate: Double) = if (candidate < current) candidate else current
    }

    private companion object {
        const val AXES = 3
        const val BOX_SIZE = 2 * AXES
        const val ROOT = 0
        const val LEAF = -1
        const val LEAF_SIZE = 4

        /** Median splits keep the tree balanced: 64 levels cover any scene that fits in memory. */
        const val STACK_DEPTH = 64
    }
}

/**
 * A [Bvh] numbered depth-first for a walk without a stack: node `n` has its box at `boxes[n * 6 until n * 6 + 6]`,
 * the first node after its subtree at `skip[n]`, and, for a leaf, objects `order[first[n] until first[n] + count[n]]`;
 * an inner node has `count[n] == 0` and its left child at `n + 1`.
 */
internal class FlatBvh(
    val order: IntArray,
    val boxes: DoubleArray,
    val skip: IntArray,
    val first: IntArray,
    val count: IntArray,
) {
    val nodeCount: Int get() = skip.size
}
