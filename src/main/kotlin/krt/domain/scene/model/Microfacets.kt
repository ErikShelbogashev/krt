package krt.domain.scene.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import krt.platform.math.Color
import krt.platform.math.Point3

/**
 * A rough surface as a field of tiny mirrors whose slopes follow the GGX distribution, [alphaX] wide along the
 * surface's tangent and [alphaY] across it (ADR 0013). The tangent runs around the vertical axis, so an anisotropic
 * surface looks brushed in circles around it, like a turned metal part.
 */
class Microfacets(val alphaX: Double, val alphaY: Double) {

    /** The tangent, the bitangent and [normal]: the frame the slopes are measured in. */
    class Frame(val tangent: Point3, val bitangent: Point3, val normal: Point3) {
        fun local(w: Point3): Point3 = Point3(w.dot(tangent), w.dot(bitangent), w.dot(normal))
        fun world(w: Point3): Point3 = tangent * w.x + bitangent * w.y + normal * w.z
    }

    /**
     * The normal of a mirror that the direction [toViewer] (in [frame], away from the surface) sees, drawn in
     * proportion to how much of it the viewer sees: Heitz's sampling of visible normals.
     */
    fun visibleNormal(toViewer: Point3, random: Random): Point3 {
        val stretched = Point3(alphaX * toViewer.x, alphaY * toViewer.y, toViewer.z).unit()
        val lengthSquared = stretched.x * stretched.x + stretched.y * stretched.y
        val t1 = if (lengthSquared > 0) Point3(-stretched.y, stretched.x, 0.0) / sqrt(lengthSquared) else X_AXIS
        val t2 = stretched.cross(t1)
        val radius = sqrt(random.nextDouble())
        val phi = 2 * PI * random.nextDouble()
        val p1 = radius * cos(phi)
        val blend = (1 + stretched.z) / 2
        val p2 = (1 - blend) * sqrt(1 - p1 * p1) + blend * radius * sin(phi)
        val onHemisphere = t1 * p1 + t2 * p2 + stretched * sqrt(maxOf(0.0, 1 - p1 * p1 - p2 * p2))
        return Point3(alphaX * onHemisphere.x, alphaY * onHemisphere.y, maxOf(0.0, onHemisphere.z)).unit()
    }

    /** Smith's Λ of a direction in the frame: how much of the surface the slopes hide from it. */
    fun lambda(w: Point3): Double {
        val cosSquared = w.z * w.z
        if (cosSquared == 0.0) return Double.POSITIVE_INFINITY
        val slopes = (alphaX * alphaX * w.x * w.x + alphaY * alphaY * w.y * w.y) / cosSquared
        return (sqrt(1 + slopes) - 1) / 2
    }

    /** The share of the light along [incoming] that sampling visible normals from [outgoing] lets through: G2 / G1. */
    fun shadowing(outgoing: Point3, incoming: Point3): Double {
        val viewer = lambda(outgoing.abs())
        return (1 + viewer) / (1 + viewer + lambda(incoming.abs()))
    }

    private fun Point3.abs(): Point3 = Point3(x, y, abs(z))

    companion object {
        private val X_AXIS = Point3(1.0, 0.0, 0.0)
        private val UP = Point3(0.0, 1.0, 0.0)
        private const val NEARLY_VERTICAL = 0.999

        /** Width of the slopes for a perceptual [roughness] (Disney's α = roughness²) and [anisotropy] in [0, 1). */
        fun of(roughness: Double, anisotropy: Double = 0.0): Microfacets {
            val alpha = maxOf(roughness * roughness, MIN_ALPHA)
            val aspect = sqrt(1 - ANISOTROPY_SPREAD * anisotropy)
            return Microfacets(alpha / aspect, alpha * aspect)
        }

        /** The frame around [normal], its tangent around the vertical axis. */
        fun frame(normal: Point3): Frame {
            val axis = if (abs(normal.y) < NEARLY_VERTICAL) UP else X_AXIS
            val tangent = axis.cross(normal).unit()
            return Frame(tangent, normal.cross(tangent), normal)
        }

        /**
         * The frame around [normal] with its tangent along [direction] laid on the surface, like the brushing of a
         * flat metal part; around the vertical axis where [direction] stands straight out of the surface.
         */
        fun frame(normal: Point3, direction: Point3): Frame {
            val along = direction - normal * direction.dot(normal)
            if (along.length() < MIN_TANGENT) return frame(normal)
            val tangent = along.unit()
            return Frame(tangent, normal.cross(tangent), normal)
        }

        private const val MIN_TANGENT = 1e-6

        private const val MIN_ALPHA = 1e-4
        private const val ANISOTROPY_SPREAD = 0.9
    }
}

/** Fresnel reflectance of a metal, from its complex refraction index [eta] + i[k] per channel. */
object ConductorFresnel {
    fun reflectance(cosine: Double, eta: Color, k: Color): Color =
        Color(channel(cosine, eta.x, k.x), channel(cosine, eta.y, k.y), channel(cosine, eta.z, k.z))

    /** The exact unpolarised reflectance of a conductor. */
    fun channel(cosine: Double, eta: Double, k: Double): Double {
        val cos2 = cosine * cosine
        val sin2 = 1 - cos2
        val t0 = eta * eta - k * k - sin2
        val a2b2 = sqrt(t0 * t0 + 4 * eta * eta * k * k)
        val a = sqrt(maxOf(0.0, (a2b2 + t0) / 2))
        val t1 = a2b2 + cos2
        val t2 = 2 * a * cosine
        val s = (t1 - t2) / (t1 + t2)
        val t3 = cos2 * a2b2 + sin2 * sin2
        val t4 = t2 * sin2
        val p = s * (t3 - t4) / (t3 + t4)
        return (s + p) / 2
    }
}
