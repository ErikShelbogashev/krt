package krt.domain.scene.model

import krt.platform.math.Aabb
import krt.platform.math.Color
import krt.platform.math.Point3

/** What the scene list shows of one object: a readable [name], its material's [color] and its [position]. */
data class ObjectSummary(
    val id: String,
    val name: String,
    val color: Color,
    val position: Point3,
)

/** Objects of one kind, listed together under [title]. */
data class ObjectGroup(val title: String, val objects: List<ObjectSummary>)

/**
 * The scene as a list: [featured] objects one by one (named or large ones), the rest in [groups] by kind,
 * so a scene of hundreds of small spheres or a loaded model stays readable.
 */
data class SceneCatalog(val featured: List<ObjectSummary>, val groups: List<ObjectGroup>) {

    val size: Int get() = featured.size + groups.sumOf { it.objects.size }

    /** The object picked by [id], or the first listed one until one is picked. */
    fun selected(id: String?): ObjectSummary? = find(id) ?: featured.firstOrNull()

    fun find(id: String?): ObjectSummary? =
        featured.find { it.id == id } ?: groups.firstNotNullOfOrNull { group -> group.objects.find { it.id == id } }

    companion object {
        /** Spheres at least this large are listed one by one. */
        private const val FEATURED_RADIUS = 0.5
        private val GLASS_COLOR = Color(0.85, 0.92, 1.0)
        private val UNKNOWN_COLOR = Color(0.5, 0.5, 0.5)

        /**
         * The catalog of [objects]: the parts that share an id are one object, as the triangles of one mesh are,
         * listed once, at the centre of them all; so every listed object has an id of its own.
         */
        fun of(objects: List<Traceable>): SceneCatalog {
            val (featured, rest) = objects.groupBy { it.id }.values.partition { isFeatured(it.first()) }
            val groups = rest.groupBy { groupTitle(it.first()) }
                .map { (title, members) -> ObjectGroup(title, members.map(::summaryOf)) }
            return SceneCatalog(featured.map(::summaryOf), groups)
        }

        private fun summaryOf(parts: List<Traceable>): ObjectSummary {
            val boxes = parts.map { it.bounds() }
            val min = Point3(boxes.minOf { it.min.x }, boxes.minOf { it.min.y }, boxes.minOf { it.min.z })
            val max = Point3(boxes.maxOf { it.max.x }, boxes.maxOf { it.max.y }, boxes.maxOf { it.max.z })
            return summary(parts.first()).copy(position = Aabb(min, max).center)
        }

        fun summary(obj: Traceable): ObjectSummary {
            val material = materialOf(obj)
            val name = if (hasCustomId(obj)) obj.id else "${kindOf(obj)} · ${materialName(material)}"
            return ObjectSummary(obj.id, name, mediumOf(obj)?.albedo ?: materialColor(material), obj.bounds().center)
        }

        private fun isFeatured(obj: Traceable): Boolean =
            hasCustomId(obj) || (obj is Sphere && obj.radius >= FEATURED_RADIUS)

        /** Objects get generated ids like `Sphere[uuid]` unless the scene names them. */
        private fun hasCustomId(obj: Traceable): Boolean = !obj.id.startsWith("${kindOf(obj)}[")

        private fun groupTitle(obj: Traceable): String = when (obj) {
            is Sphere -> "Small spheres"
            is Triangle -> "Triangles"
            else -> kindOf(obj)
        }

        private fun kindOf(obj: Traceable): String = obj::class.simpleName ?: "Object"

        private fun materialOf(obj: Traceable): Material? = when (obj) {
            is Sphere -> obj.material
            is Triangle -> obj.material
            is Fog -> Interface
            is Translucent -> obj.surface.material
            else -> null
        }

        /** The medium a bank of fog or a translucent ball holds, whose colour the list shows. */
        private fun mediumOf(obj: Traceable): Medium? = when (obj) {
            is Fog -> obj.medium
            is Translucent -> obj.medium
            else -> null
        }

        private fun materialName(material: Material?): String = when (material) {
            is Lambert -> "Lambert"
            is Metal -> "Metal"
            is Dielectric -> "Glass"
            is Emissive -> "Emissive"
            is Interface -> "Medium"
            is Conductor -> "Metal"
            null -> "Unknown"
            else -> material::class.simpleName ?: "Material"
        }

        private fun materialColor(material: Material?): Color = when (material) {
            is Lambert -> material.albedo
            is Metal -> material.albedo
            is Emissive -> material.emitColor
            is Dielectric -> GLASS_COLOR
            is Conductor -> ConductorFresnel.reflectance(1.0, material.eta, material.k)
            is Velvet -> material.base
            is Iridescent -> material.base
            else -> UNKNOWN_COLOR
        }
    }
}
