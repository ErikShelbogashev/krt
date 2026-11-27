package krt.domain.scene.model

import krt.platform.math.Point3
import krt.platform.math.Ray

/** Where a ray met a surface; [media] the media on the surface's two sides, when it bounds any (ADR 0014). */
data class Hit(
    val p: Point3,
    val t: Double,
    val normal: Point3,
    val isFrontFace: Boolean,
    val material: Material,
    val media: MediumInterface? = null,
) {
    companion object {
        fun create(
            p: Point3,
            t: Double,
            ray: Ray,
            outwardNormal: Point3,
            material: Material,
            media: MediumInterface? = null,
        ): Hit {
            val isFrontFace = ray.direction.dot(outwardNormal) < 0
            val normal = if (isFrontFace) outwardNormal else -outwardNormal
            return Hit(p, t, normal, isFrontFace, material, media)
        }
    }
}