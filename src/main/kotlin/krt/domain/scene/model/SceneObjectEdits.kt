package krt.domain.scene.model

/** Update the outside medium of boundaries directly enclosed by an edited volume as well as its own inside. */
internal object SceneObjectEdits {
    fun change(
        objects: List<Traceable>, id: String, edit: ObjectChange, owner: (Traceable) -> String?,
    ): List<Traceable> = when (edit) {
        is ObjectChange.Extinction -> changeExtinction(objects, id, edit, owner)
        else -> objects.map { if (it.id == id) edit.applyTo(it) else it }
    }

    private fun changeExtinction(
        objects: List<Traceable>, id: String, edit: ObjectChange.Extinction, owner: (Traceable) -> String?,
    ): List<Traceable> {
        val replacements = mutableMapOf<Medium, Medium>()
        val updated = objects.map { part ->
            if (part.id != id) part else edit.applyTo(part).also { changed ->
                val before = ObjectOptics.inside(part)
                val after = ObjectOptics.inside(changed)
                if (before != null && after != null && before != after) replacements[before] = after
            }
        }
        if (replacements.isEmpty()) return updated
        return updated.map { part ->
            val next = replacements[outside(part)]
            if (next != null && part.id != id && owner(part) == id) withOutside(part, next) else part
        }
    }

    fun outside(part: Traceable): Medium? = when (part) {
        is Fog -> part.outside
        is Translucent -> part.outside
        is Sphere -> part.media?.outside
        is Triangle -> part.media?.outside
        else -> null
    }

    private fun withOutside(part: Traceable, medium: Medium): Traceable = when (part) {
        is Fog -> Fog(part.center, part.radius, part.medium, part.id, medium)
        is Translucent -> Translucent(part.center, part.radius, part.medium, part.ior, part.roughness, part.id, medium)
        is Sphere -> Sphere(part.center, part.radius, part.material, part.id, part.velocity,
            part.media?.copy(outside = medium))
        is Triangle -> Triangle(part.v0, part.v1, part.v2, part.material, part.id, part.normals,
            part.media?.copy(outside = medium))
        else -> part
    }
}
