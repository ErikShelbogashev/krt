package krt.domain.scene.model

import kotlin.math.abs
import krt.platform.math.Point3
import krt.platform.math.Ray

/**
 * Where a ray met a surface; [media] the media on the surface's two sides, when it bounds any (ADR 0014). A surface
 * that light sampling cannot reach, a fractal, is not [lightSampled]: its glow counts on every bounce (ADR 0012).
 */
data class Hit(
    val p: Point3,
    val t: Double,
    val normal: Point3,
    val isFrontFace: Boolean,
    val material: Material,
    val media: MediumInterface? = null,
    val lightSampled: Boolean = true,
) {
    companion object {
        /** Rare fallback when squaring a finite direction underflows or overflows the intersection equation. */
        internal fun withScaledDirection(shape: Traceable, ray: Ray, tMin: Double, tMax: Double): Hit? {
            val d = ray.direction
            val scale = maxOf(abs(d.x), abs(d.y), abs(d.z))
            if (!(scale > 0.0) || !scale.isFinite()) return null
            val scaled = ray.next(ray.origin, d / scale)
            return shape.hit(scaled, tMin * scale, tMax * scale)?.let { hit ->
                val t = hit.t / scale
                if (t.isFinite() && t in tMin..tMax) hit.copy(t = t) else null
            }
        }

        fun create(
            p: Point3,
            t: Double,
            ray: Ray,
            outwardNormal: Point3,
            material: Material,
            media: MediumInterface? = null,
            lightSampled: Boolean = true,
        ): Hit {
            val isFrontFace = ray.direction.dot(outwardNormal) < 0
            val normal = if (isFrontFace) outwardNormal else -outwardNormal
            return Hit(p, t, normal, isFrontFace, material, media, lightSampled)
        }
    }
}