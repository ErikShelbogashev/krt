package krt.domain.scene.model

import krt.platform.math.Color

data class SelectedOptics(val fractal: Fractal?, val emission: Double?, val medium: Medium?)

/** Optical properties of a selected object. Edits replace geometry while preserving its identity and placement. */
object ObjectOptics {
    fun selected(objects: List<Traceable>, id: String): SelectedOptics {
        val parts = objects.filter { it.id == id }
        return SelectedOptics(parts.firstNotNullOfOrNull { it as? Fractal },
            parts.firstNotNullOfOrNull(::emissionScale), parts.firstNotNullOfOrNull(::inside))
    }

    fun emissionScale(obj: Traceable): Double? = when (val material = materialOf(obj)) {
        is Emissive -> material.intensity
        is GlowingPattern -> material.intensity
        else -> null
    }

    fun withEmission(obj: Traceable, scale: Double): Traceable {
        require(scale.isFinite() && scale >= 0.0)
        val light = when (val original = materialOf(obj)) {
            is Emissive -> Emissive(original.emitColor, scale, original.emissionSide)
            is GlowingPattern -> GlowingPattern(original.pattern, scale)
            else -> return obj
        }
        return when (obj) {
            is Sphere -> Sphere(obj.center, obj.radius, light, obj.id, obj.velocity, obj.media)
            is Triangle -> Triangle(obj.v0, obj.v1, obj.v2, light, obj.id, obj.normals, obj.media)
            is Fractal -> Fractal(obj.kind, obj.center, obj.radius, light, obj.id,
                obj.iterations, obj.parameter, obj.seed)
            else -> obj
        }
    }

    fun inside(obj: Traceable): Medium? = when (obj) {
        is Fog -> obj.medium
        is Translucent -> obj.medium
        is Triangle -> obj.media?.inside
        is Sphere -> obj.media?.inside
        else -> null
    }

    fun withDensity(obj: Traceable, density: Color): Traceable {
        require(density.x.isFinite() && density.y.isFinite() && density.z.isFinite())
        require(minOf(density.x, density.y, density.z) >= 0.0)
        val medium = inside(obj)?.copy(density = density) ?: return obj
        return when (obj) {
            is Fog -> Fog(obj.center, obj.radius, medium, obj.id, obj.outside)
            is Translucent -> Translucent(obj.center, obj.radius, medium, obj.ior, obj.roughness, obj.id, obj.outside)
            is Triangle -> Triangle(obj.v0, obj.v1, obj.v2, obj.material, obj.id, obj.normals,
                obj.media?.copy(inside = medium))
            is Sphere -> Sphere(obj.center, obj.radius, obj.material, obj.id, obj.velocity,
                obj.media?.copy(inside = medium))
            else -> obj
        }
    }

    private fun materialOf(obj: Traceable): Material? = when (obj) {
        is Triangle -> obj.material
        is Sphere -> obj.material
        is Fractal -> obj.material
        else -> null
    }
}
