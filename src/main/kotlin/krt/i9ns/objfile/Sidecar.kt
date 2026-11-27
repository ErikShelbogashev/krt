package krt.i9ns.objfile

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import krt.platform.math.Point3

/** One material of a `render-showcase.json`: its type and fields as given. */
class SidecarMaterial(val name: String, val type: String, val fields: JsonObject) {
    fun number(key: String, default: Double): Double = fields[key]?.jsonPrimitive?.doubleOrNull ?: default

    fun text(key: String): String? = (fields[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    fun color(key: String): Point3? = Sidecar.point(fields[key])

    fun part(key: String): JsonObject? = fields[key] as? JsonObject
}

/** A fog volume of a `render-showcase.json`: a sphere of homogeneous medium, in the file's coordinates. */
class SidecarVolume(val name: String, val center: Point3, val radius: Double, val density: Double, val albedo: Point3)

/** A binding of a `render-showcase.json`: object name globs and the material they take. */
class SidecarBinding(val globs: List<String>, val material: String)

/**
 * The look-development sidecar of an OBJ scene (`render-showcase.json`, schema krt-showcase-target-v1) and its
 * layout (`scene-layout.json`): what krt reads of them (ADR 0016). A pure transformation of JSON text.
 */
class Sidecar(
    val materials: Map<String, SidecarMaterial>,
    val bindings: List<SidecarBinding>,
    val volumes: List<SidecarVolume>,
    val lightGlobs: List<String>,
    val sampleLights: Boolean,
) {
    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }

        fun of(text: String): Sidecar {
            val root = JSON.parseToJsonElement(text).jsonObject
            val materials = root["materials"]?.jsonObject.orEmpty().mapValues { (name, value) ->
                val fields = value.jsonObject
                SidecarMaterial(name, fields["type"]?.jsonPrimitive?.content ?: "Lambert", fields)
            }
            val bindings = root["bindings"]?.jsonArray.orEmpty().map { binding ->
                val fields = binding.jsonObject
                SidecarBinding(strings(fields["objects"]), fields["material"]?.jsonPrimitive?.content.orEmpty())
            }
            val volumes = root["volumes"]?.jsonArray.orEmpty().map { volume ->
                val fields = volume.jsonObject
                SidecarVolume(
                    fields["name"]?.jsonPrimitive?.content ?: "volume",
                    point(fields["center"]) ?: Point3.ZERO,
                    fields["radius"]?.jsonPrimitive?.doubleOrNull ?: 1.0,
                    fields["density"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    point(fields["albedo"]) ?: Point3.ONE,
                )
            }
            val lights = root["light_sampling"]?.jsonObject
            return Sidecar(
                materials, bindings, volumes, strings(lights?.get("include")),
                lights?.get("enabled")?.jsonPrimitive?.content?.toBoolean() ?: true,
            )
        }

        fun point(element: JsonElement?): Point3? = (element as? JsonArray)?.map { it.jsonPrimitive.content.toDouble() }
            ?.takeIf { it.size >= AXES }?.let { Point3(it[0], it[1], it[2]) }

        private fun strings(element: JsonElement?): List<String> =
            (element as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()

        private const val AXES = 3
    }
}

/** What krt reads of a `scene-layout.json`: the suggested camera, the world colour, each object's rotation. */
class SceneLayout(
    val zUp: Boolean,
    val cameraPosition: Point3?,
    val cameraTarget: Point3?,
    val focalLength: Double?,
    val worldColor: String?,
    val rotations: Map<String, Point3>,
) {
    companion object {
        fun of(text: String): SceneLayout {
            val root = Json.parseToJsonElement(text).jsonObject
            val camera = root["camera_suggestion"]?.jsonObject
            val rotations = root["objects"]?.jsonArray.orEmpty().associate { obj ->
                val fields = obj.jsonObject
                val rotation = Sidecar.point(fields["rotation_deg"]) ?: Point3.ZERO
                fields["name"]?.jsonPrimitive?.content.orEmpty() to rotation
            }
            return SceneLayout(
                zUp = root["coordinate_system"]?.jsonPrimitive?.content?.contains("Z-up", ignoreCase = true) == true,
                cameraPosition = Sidecar.point(camera?.get("position")),
                cameraTarget = Sidecar.point(camera?.get("target")),
                focalLength = camera?.get("focal_length_mm")?.jsonPrimitive?.doubleOrNull,
                worldColor = root["lighting_suggestion"]?.jsonObject?.get("world_color")?.jsonPrimitive?.content,
                rotations = rotations,
            )
        }
    }
}
