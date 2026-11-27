package krt.i9ns.objfile

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import krt.domain.scene.model.Blackbody
import krt.domain.scene.model.Conductor
import krt.domain.scene.model.Dielectric
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.GlowingPattern
import krt.domain.scene.model.Iridescent
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Material
import krt.domain.scene.model.Medium
import krt.domain.scene.model.Metals
import krt.domain.scene.model.Noise
import krt.domain.scene.model.Pattern
import krt.domain.scene.model.PatternKind
import krt.domain.scene.model.Patterned
import krt.domain.scene.model.Plastic
import krt.domain.scene.model.SoapBubble
import krt.domain.scene.model.Velvet
import krt.platform.math.Color
import krt.platform.math.Matrix4
import krt.platform.math.Point3

/** A material of an OBJ scene and the [medium] inside the objects it covers, for subsurface scattering. */
class ObjMaterial(val material: Material, val medium: Medium? = null)

/**
 * The materials of `render-showcase.json` as krt materials (ADR 0016), the MTL colour where no binding matches:
 * a pure transformation. [rotations] of the layout give the axes `tangent_axis` names; [up] turns them to Y-up.
 */
class SidecarMaterials(
    private val materials: Map<String, SidecarMaterial>,
    private val mtl: Map<String, MtlMaterial>,
    private val rotations: Map<String, Point3>,
    private val up: Matrix4,
) {
    fun of(obj: ObjObject, bound: SidecarMaterial?): ObjMaterial {
        val base = mtl[obj.material]
        return bound?.let { physical(it, obj, base?.diffuse ?: GREY) } ?: ObjMaterial(fallback(base))
    }

    private fun physical(m: SidecarMaterial, obj: ObjObject, base: Color): ObjMaterial = when (m.type) {
        "Dielectric" -> ObjMaterial(
            Dielectric(
                m.number("indexOfReflection", GLASS_IOR), m.color("tint") ?: Point3.ONE, m.number("density", 0.0),
                m.number("dispersion", 0.0), m.number("roughness", 0.0),
            ),
        )
        "Conductor" -> ObjMaterial(conductor(m, obj))
        "Subsurface" -> ObjMaterial(
            Dielectric(SKIN_IOR, roughness = m.number("surface_roughness", SUBSURFACE_ROUGHNESS)),
            Medium.ofMeanFreePath(m.color("mean_free_path") ?: Point3.ONE, m.color("albedo") ?: base),
        )
        "Emissive" -> ObjMaterial(emissive(m))
        else -> ObjMaterial(surface(m, base))
    }

    private fun surface(m: SidecarMaterial, base: Color): Material = when (m.type) {
        "Sheen" -> Velvet(m.color("base") ?: base, Point3.ONE * m.number("sheen", 1.0), m.number("roughness", VELVET))
        "Plastic" -> plastic(m, m.color("albedo") ?: base)
        "SoapBubble" -> SoapBubble(m.number("thickness", 0.0), m.number("filmIor", WATER_IOR))
        "Iridescent" -> Iridescent(
            m.color("base") ?: base, m.number("thickness", 0.0), m.number("filmIor", GLASS_IOR),
            m.number("roughness", 0.0),
        )
        "Lambert" -> Lambert(m.color("albedo") ?: base)
        else -> throw ObjSceneError("material ${m.name}: type ${m.type} unknown")
    }

    private fun conductor(m: SidecarMaterial, obj: ObjObject): Conductor {
        val metal = Metals.valueOf((m.text("metal") ?: "SILVER").uppercase())
        val axis = when (m.text("tangent_axis")) {
            "object_x" -> Point3(1.0, 0.0, 0.0)
            "object_y" -> Point3(0.0, 1.0, 0.0)
            "object_z" -> Point3(0.0, 0.0, 1.0)
            else -> null
        }
        val brushing = axis?.let { up.vector(rotation(rotations[obj.name] ?: Point3.ZERO).vector(it)) }
        return Conductor(metal, m.number("roughness", 0.0), m.number("anisotropy", 0.0), brushing)
    }

    private fun plastic(m: SidecarMaterial, albedo: Color): Material {
        val roughness = m.number("roughness", 0.0)
        val texture = m.part("texture") ?: return Plastic(albedo, roughness)
        val bump = m.part("bump")?.number("strength") ?: 0.0
        return Patterned(pattern(texture, albedo), roughness, bump)
    }

    /** The pattern a sidecar texture describes, in shades of [base]. */
    private fun pattern(texture: JsonObject, base: Color): Pattern {
        val scale = texture.number("scale") ?: 1.0
        return when (val type = texture["type"]?.jsonPrimitive?.content) {
            "wood" -> Pattern(
                PatternKind.WOOD, light(base, EARLYWOOD), base * LATEWOOD, scale,
                texture.number("distortion") ?: Pattern.DEFAULT_TURBULENCE, texture.number("rings") ?: 1.0,
            )
            "perlin" -> Pattern(
                PatternKind.NOISE, base * DARKER, light(base, LIGHTER), scale,
                octaves = texture.number("octaves")?.toInt() ?: Noise.OCTAVES,
            )
            "marble" -> Pattern(
                PatternKind.MARBLE, light(base, 1.0), base * VEINS, scale,
                texture.number("turbulence") ?: Pattern.DEFAULT_TURBULENCE, texture.number("vein_frequency") ?: 1.0,
            )
            else -> throw ObjSceneError("texture type $type unknown")
        }
    }

    /**
     * Light of a colour temperature (linear RGB of luminance 1), or of its `color_override`, times its intensity;
     * a grid or pulsing stripes glow in a pattern, an `object_mask` is the whole object glowing.
     */
    private fun emissive(m: SidecarMaterial): Material {
        val color = m.color("color_override") ?: Blackbody.color(m.number("temperature_kelvin", DAYLIGHT_K))
        val intensity = m.number("intensity", 1.0)
        val texture = m.part("texture")
        return when (val type = texture?.get("type")?.jsonPrimitive?.content) {
            null, "object_mask" -> Emissive(color, intensity)
            "procedural_grid" -> {
                val cell = texture.number("cell") ?: 1.0
                val line = texture.number("line_width") ?: cell / GRID_LINE
                val grid = Pattern(PatternKind.GRID, color * DIM, color, 1 / cell, frequency = line / cell)
                GlowingPattern(grid, intensity)
            }
            "pulse_stripes" -> {
                val frequency = texture.number("frequency") ?: 1.0
                GlowingPattern(Pattern(PatternKind.STRIPES, color * DIM, color, frequency = frequency), intensity)
            }
            else -> throw ObjSceneError("emission texture $type unknown")
        }
    }

    private fun fallback(base: MtlMaterial?): Material = when {
        base == null -> Lambert(GREY)
        base.emission != Point3.ZERO -> Emissive(base.emission, 1.0)
        base.dissolve < 1 -> Dielectric(base.ior)
        else -> Lambert(base.diffuse)
    }

    private fun light(base: Color, factor: Double): Color = (base * factor).let {
        Color(minOf(it.x, 1.0), minOf(it.y, 1.0), minOf(it.z, 1.0))
    }

    private fun JsonObject.number(key: String): Double? = get(key)?.jsonPrimitive?.doubleOrNull

    /** The rotation of Euler angles in degrees about x, then y, then z, as Blender's default XYZ order. */
    private fun rotation(degrees: Point3): Matrix4 =
        Matrix4.rotate(degrees.z, Point3(0.0, 0.0, 1.0)) * Matrix4.rotate(degrees.y, Point3(0.0, 1.0, 0.0)) *
            Matrix4.rotate(degrees.x, Point3(1.0, 0.0, 0.0))

    private companion object {
        val GREY = Color(0.5, 0.5, 0.5)
        const val GLASS_IOR = 1.5
        const val SKIN_IOR = 1.4
        const val WATER_IOR = 1.33
        const val SUBSURFACE_ROUGHNESS = 0.3
        const val VELVET = 0.5
        const val EARLYWOOD = 1.25
        const val LATEWOOD = 0.45
        const val DARKER = 0.6
        const val LIGHTER = 1.4
        const val VEINS = 0.2
        const val DIM = 0.08
        const val GRID_LINE = 10.0
        const val DAYLIGHT_K = 6500.0
    }
}
