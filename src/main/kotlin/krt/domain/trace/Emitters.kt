package krt.domain.trace

import kotlin.math.PI
import krt.domain.scene.model.Material
import krt.domain.scene.model.Sphere
import krt.domain.scene.model.Traceable
import krt.domain.scene.model.Triangle
import krt.platform.math.Point3

/**
 * One light of a scene for light sampling (ADR 0012): a sphere, or the triangles of one object, which share its
 * id, like a letter of a neon sign. [areas] are those of its [members]; [weight], its area times the brightness of
 * its light, is how often light sampling picks it among the others.
 */
class Emitter(val members: List<Traceable>, val areas: DoubleArray, val weight: Double) {
    val area: Double = areas.sum()
}

/** Grouping of the emissive spheres and triangles of a scene into lights: a pure transformation. */
object Emitters {

    /** Weights of the red, green and blue channels in the brightness of light (Rec. 709 luminance). */
    private val LUMINANCE = Point3(0.2126, 0.7152, 0.0722)

    /** The lights of [objects], in the order they first appear. */
    fun of(objects: List<Traceable>): List<Emitter> =
        objects.filter { materialOf(it)?.emits == true }
            .groupBy<Traceable, Any> { if (it is Triangle) it.id else it }
            .values.map { members ->
                val areas = DoubleArray(members.size) { areaOf(members[it]) }
                val brightness = members.sumOf { luminance(it) * areaOf(it) } / areas.sum()
                Emitter(members, areas, areas.sum() * maxOf(brightness, MIN_BRIGHTNESS))
            }

    fun materialOf(obj: Traceable): Material? = (obj as? Sphere)?.material ?: (obj as? Triangle)?.material

    fun areaOf(obj: Traceable): Double = when (obj) {
        is Sphere -> SPHERE_AREA * PI * obj.radius * obj.radius
        is Triangle -> (obj.v1 - obj.v0).cross(obj.v2 - obj.v0).length() / 2
        else -> 0.0
    }

    /** Brightness of the light [obj] gives off at its centre: patterns may glow unevenly, so this only weighs. */
    private fun luminance(obj: Traceable): Double {
        val center = obj.bounds().center
        return materialOf(obj)?.emitted(0.0, 0.0, center)?.dot(LUMINANCE) ?: 0.0
    }

    private const val SPHERE_AREA = 4

    /** A light too dim to weigh is still picked now and then, so the estimate keeps it. */
    private const val MIN_BRIGHTNESS = 1e-3
}
