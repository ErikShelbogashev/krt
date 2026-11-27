package krt.domain.trace

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import krt.domain.scene.World
import krt.domain.scene.model.Traceable
import krt.platform.math.Point3
import krt.platform.math.Ray

class FlatBvhTest {

    private val objects = World.default(Random(42)).objects

    @Test
    fun `leaves hold every object once, in the order of the walk`() {
        val flat = Bvh(objects.toTypedArray()).flatten()
        val leaves = (0 until flat.nodeCount).filter { flat.count[it] > 0 }
        val ranges = leaves.flatMap { flat.first[it] until flat.first[it] + flat.count[it] }
        assertEquals(objects.indices.toList(), ranges)
        assertEquals(objects.indices.toSet(), flat.order.toSet())
    }

    @Test
    fun `skip links lead past the subtree`() {
        val flat = Bvh(objects.toTypedArray()).flatten()
        assertEquals(flat.nodeCount, flat.skip[0])
        (0 until flat.nodeCount).forEach { node -> assertTrue(flat.skip[node] > node) }
    }

    @Test
    fun `a walk without a stack finds the same hit as the tree`() {
        val bvh = Bvh(objects.toTypedArray())
        val flat = bvh.flatten()
        val random = Random(7)
        repeat(RAYS) {
            val ray = Ray(Point3.random(random, -RANGE, RANGE), Point3.random(random, -1.0, 1.0))
            assertEquals(bvh.hit(ray, T_MIN, Double.POSITIVE_INFINITY)?.t, walk(flat, objects, ray))
        }
    }

    @Test
    fun `an empty scene has no nodes`() {
        assertEquals(0, Bvh(emptyArray()).flatten().nodeCount)
    }

    /** The walk of the GPU shader, written out in Kotlin. */
    private fun walk(flat: FlatBvh, objects: List<Traceable>, ray: Ray): Double? {
        var closest = Double.POSITIVE_INFINITY
        var node = 0
        while (node < flat.nodeCount) {
            if (!entersBox(flat.boxes, node, ray, closest)) {
                node = flat.skip[node]
                continue
            }
            for (k in flat.first[node] until flat.first[node] + flat.count[node]) {
                objects[flat.order[k]].hit(ray, T_MIN, closest)?.let { closest = it.t }
            }
            node++
        }
        return closest.takeIf { it.isFinite() }
    }

    private fun entersBox(boxes: DoubleArray, node: Int, ray: Ray, far: Double): Boolean {
        var near = T_MIN
        var exit = far
        for (axis in 0 until 3) {
            val inverse = 1.0 / ray.direction[axis]
            val t1 = (boxes[node * 6 + axis] - ray.origin[axis]) * inverse
            val t2 = (boxes[node * 6 + 3 + axis] - ray.origin[axis]) * inverse
            near = maxOf(near, minOf(t1, t2))
            exit = minOf(exit, maxOf(t1, t2))
        }
        return exit >= near
    }

    private companion object {
        const val RAYS = 500
        const val RANGE = 12.0
        const val T_MIN = 0.001
    }
}
