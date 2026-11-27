package krt.domain.render

import kotlin.random.Random
import krt.domain.scene.World
import krt.domain.trace.PathTracing
import krt.platform.math.Color
import krt.platform.math.Ray

class RayTracer(val world: World, var maxDepth: Int) {
    fun color(ray: Ray, random: Random): Color = PathTracing.color(world.traceScene, ray, maxDepth, random)
}
