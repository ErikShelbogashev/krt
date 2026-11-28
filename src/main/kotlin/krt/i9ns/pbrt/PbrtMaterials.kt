package krt.i9ns.pbrt

import kotlin.math.sqrt
import krt.domain.scene.model.Blackbody
import krt.domain.scene.model.Conductor
import krt.domain.scene.model.Dielectric
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.Interface
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Material
import krt.domain.scene.model.Medium
import krt.domain.scene.model.Metal
import krt.domain.scene.model.Metals
import krt.domain.scene.model.Pattern
import krt.domain.scene.model.PatternKind
import krt.domain.scene.model.Patterned
import krt.domain.scene.model.Plastic
import krt.domain.scene.model.SoapBubble
import krt.i9ns.pbrt.PbrtValues.color
import krt.i9ns.pbrt.PbrtValues.fromPbrtRoughness
import krt.platform.math.Color
import krt.platform.math.Point3

/** A material read from a scene file, with the [medium] inside the shapes it covers, when it has one. */
data class MaterialSpec(val material: Material, val medium: Medium? = null)

/**
 * pbrt-v4 materials, area lights and media as krt draws them (ADR 0016): pure transformations. pbrt materials krt
 * has no twin for get the nearest krt one and a warning.
 */
object PbrtMaterials {

    private val GREY = Color(0.5, 0.5, 0.5)
    private val SUBSURFACE_ALBEDO = Color(0.9, 0.9, 0.9)
    private const val ANISOTROPY_SPREAD = 0.9
    private const val DEFAULT_BUMP = 0.25

    /** pbrt allows g in (-1, 1); at ±1 the phase function is a single direction. */
    private const val MAX_G = 0.99

    /** Named metals of pbrt-v4 by their krt twins. */
    private val METALS =
        mapOf("Au" to Metals.GOLD, "Cu" to Metals.COPPER, "Ag" to Metals.SILVER, "Al" to Metals.ALUMINIUM)

    /** A pbrt material directive (`Material` or the parameters of `MakeNamedMaterial`) of [type]. */
    fun of(
        type: String,
        d: PbrtDirective,
        textures: Map<String, Pattern>,
        warnings: MutableList<String>,
    ): MaterialSpec =
        when (type) {
            "diffuse" -> MaterialSpec(diffuse(d, textures))
            "coateddiffuse" -> MaterialSpec(coated(d, textures))
            "conductor", "coatedconductor" -> MaterialSpec(conductor(d, warnings))
            "dielectric" -> MaterialSpec(dielectric(d, warnings))
            "thindielectric" -> MaterialSpec(SoapBubble(0.0, PbrtValues.eta(d, warnings).first))
            "interface" -> MaterialSpec(Interface)
            "subsurface" -> subsurface(d, warnings)
            "diffusetransmission" -> {
                warnings += "line ${d.line}: diffusetransmission drawn as diffuse"
                MaterialSpec(Lambert(color(d, "reflectance", GREY)))
            }
            else -> {
                warnings += "line ${d.line}: material $type not supported, drawn as grey diffuse"
                MaterialSpec(Lambert(GREY))
            }
        }

    /** An `AreaLightSource "diffuse"`: its radiance by colour or by black-body temperature, times its scale. */
    fun areaLight(d: PbrtDirective, warnings: MutableList<String>): Material {
        if (d.string(0) != "diffuse") warnings += "line ${d.line}: area light ${d.string(0)} drawn as diffuse"
        val scale = d.double("scale", 1.0)
        val kelvin = d.params["L"]?.takeIf { it.type == "blackbody" }?.doubles()?.first()
        return kelvin?.let { Emissive(Blackbody.color(it), scale) } ?: Emissive(color(d, "L", Point3.ONE), scale)
    }

    /** A `MakeNamedMedium` of type homogeneous: extinction and albedo from its absorption and scattering. */
    fun medium(d: PbrtDirective, warnings: MutableList<String>): Medium {
        val type = d.text("type", "homogeneous")
        if (type != "homogeneous") warnings += "line ${d.line}: medium $type drawn as homogeneous"
        val scale = d.double("scale", 1.0)
        val absorption = color(d, "sigma_a", Point3.ONE) * scale
        val scattering = color(d, "sigma_s", Point3.ONE) * scale
        val extinction = absorption + scattering
        val albedo = Color(
            PbrtValues.share(scattering.x, extinction.x),
            PbrtValues.share(scattering.y, extinction.y),
            PbrtValues.share(scattering.z, extinction.z),
        )
        return Medium(extinction, albedo, d.double("g", 0.0).coerceIn(-MAX_G, MAX_G))
    }

    private fun diffuse(d: PbrtDirective, textures: Map<String, Pattern>): Material =
        textured(d, textures)?.let { Patterned(it, 1.0, bump(d)) } ?: Lambert(color(d, "reflectance", GREY))

    private fun coated(d: PbrtDirective, textures: Map<String, Pattern>): Material {
        val roughness = fromPbrtRoughness(d.double("roughness", 0.0), d.bool("remaproughness", true))
        return textured(d, textures)?.let { Patterned(it, roughness, bump(d)) }
            ?: Plastic(color(d, "reflectance", GREY), roughness, d.double("eta", PbrtValues.DEFAULT_ETA))
    }

    /** Bump strength of a `displacement` parameter, which krt reads as a bump of moderate strength. */
    private fun bump(d: PbrtDirective): Double = if (d.params.containsKey("displacement")) DEFAULT_BUMP else 0.0

    private fun conductor(d: PbrtDirective, warnings: MutableList<String>): Material {
        val remap = d.bool("remaproughness", true)
        val u = fromPbrtRoughness(d.double("uroughness", d.double("roughness", 0.0)), remap)
        val v = fromPbrtRoughness(d.double("vroughness", d.double("roughness", 0.0)), remap)
        if (d.params.containsKey("reflectance")) return Metal(color(d, "reflectance", GREY), (u + v) / 2)
        val metal = d.params["eta"]?.takeIf { it.type == "spectrum" }?.strings()?.firstOrNull()
            ?.removePrefix("metal-")?.substringBefore("-")
        val known = METALS[metal]
        if (metal != null && known == null) warnings += "line ${d.line}: metal $metal drawn as silver"
        val eta = known?.eta ?: color(d, "eta", Metals.SILVER.eta)
        val k = known?.k ?: color(d, "k", Metals.SILVER.k)
        val (roughness, anisotropy) = roughnessOf(u * u, v * v)
        return Conductor(eta, k, roughness, anisotropy)
    }

    /** Roughness and anisotropy of krt from GGX widths along and across the tangent (as `Microfacets.of`). */
    private fun roughnessOf(alphaX: Double, alphaY: Double): Pair<Double, Double> {
        val alpha = sqrt(alphaX * alphaY)
        val aspect = if (alphaX > 0) sqrt(alphaY / alphaX) else 1.0
        return sqrt(alpha) to ((1 - aspect * aspect) / ANISOTROPY_SPREAD).coerceIn(0.0, 1.0)
    }

    private fun dielectric(d: PbrtDirective, warnings: MutableList<String>): Material {
        val (ior, dispersion) = PbrtValues.eta(d, warnings)
        val roughness = fromPbrtRoughness(d.double("roughness", 0.0), d.bool("remaproughness", true))
        return Dielectric(ior, dispersion = dispersion, roughness = roughness)
    }

    private fun subsurface(d: PbrtDirective, warnings: MutableList<String>): MaterialSpec {
        val (ior, _) = PbrtValues.eta(d, warnings)
        val roughness = fromPbrtRoughness(d.double("roughness", 0.0), d.bool("remaproughness", true))
        val scale = d.double("scale", 1.0)
        val medium = if (d.params.containsKey("mfp")) {
            Medium.ofMeanFreePath(color(d, "mfp", Point3.ONE) / scale, color(d, "reflectance", SUBSURFACE_ALBEDO))
        } else {
            medium(d, warnings)
        }
        return MaterialSpec(Dielectric(ior, roughness = roughness), medium)
    }

    private fun textured(d: PbrtDirective, textures: Map<String, Pattern>): Pattern? =
        d.params["reflectance"]?.takeIf { it.type == "texture" }?.strings()?.firstOrNull()?.let { textures[it] }
}

/** pbrt-v4 textures as the nearest krt patterns (ADR 0016): a pure transformation. */
object PbrtTextures {
    private const val VARIATION_TO_TURBULENCE = 2.0
    private const val PBRT_VARIATION = 0.2
    private const val PBRT_OCTAVES = 8

    /** A pbrt `Texture`: the nearest krt pattern, or null for one krt does not draw. */
    fun of(d: PbrtDirective, textures: Map<String, Pattern>, warnings: MutableList<String>): Pattern? =
        when (val type = d.string(2)) {
            "mix" -> d.params["amount"]?.strings()?.firstOrNull()?.let { textures[it] }
                ?.copy(first = color(d, "tex1", Point3.ZERO), second = color(d, "tex2", Point3.ONE))
            "fbm", "wrinkled" -> Pattern(PatternKind.NOISE, Point3.ZERO, Point3.ONE, octaves = octaves(d))
            "windy" -> Pattern(PatternKind.CLOUDS, Point3.ZERO, Point3.ONE)
            "marble" -> Pattern(
                PatternKind.MARBLE, Point3.ZERO, Point3.ONE, d.double("scale", 1.0),
                d.double("variation", PBRT_VARIATION) * VARIATION_TO_TURBULENCE, octaves = octaves(d),
            )
            else -> {
                warnings += "line ${d.line}: texture $type not supported"
                null
            }
        }

    private fun octaves(d: PbrtDirective): Int = d.int("octaves", PBRT_OCTAVES)
}
