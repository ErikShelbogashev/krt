package krt.i9ns.pbrt

import krt.domain.scene.model.Checker
import krt.domain.scene.model.Conductor
import krt.domain.scene.model.Dielectric
import krt.domain.scene.model.EmissionSide
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.GlowingPattern
import krt.domain.scene.model.Interface
import krt.domain.scene.model.Iridescent
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Material
import krt.domain.scene.model.Metal
import krt.domain.scene.model.Metals
import krt.domain.scene.model.Pattern
import krt.domain.scene.model.Patterned
import krt.domain.scene.model.Plastic
import krt.domain.scene.model.SoapBubble
import krt.domain.scene.model.Spectrum
import krt.domain.scene.model.Velvet
import krt.i9ns.pbrt.PbrtText.n
import krt.i9ns.pbrt.PbrtText.p
import krt.platform.math.Color

/**
 * The lines that give a shape a krt material in a pbrt-v4 file (ADR 0016): a `#krt` line first where pbrt
 * cannot say it, then pbrt's nearest material. A pure transformation.
 */
object PbrtMaterialWriter {
    private const val SPECTRUM_SAMPLES = 8
    private const val HALF = 0.5
    private val BLACK = "Material \"diffuse\" ${p("rgb reflectance", Color.ZERO)}"
    private val PBRT_METALS =
        mapOf(Metals.GOLD to "Au", Metals.COPPER to "Cu", Metals.SILVER to "Ag", Metals.ALUMINIUM to "Al")

    fun lines(material: Material): List<String> = when (material) {
        is Lambert -> listOf("Material \"diffuse\" ${p("rgb reflectance", material.albedo)}")
        is Metal -> listOf(
            "#krt Material \"metal\" ${p("rgb albedo", material.albedo)} ${p("float fuzz", material.fuzz)}",
            "Material \"conductor\" ${p("rgb reflectance", material.albedo)} " +
                p("float roughness", PbrtValues.toPbrtRoughness(material.fuzz)),
        )
        is Dielectric -> dielectric(material)
        is Plastic -> listOf(
            "#krt Material \"plastic\" ${p("rgb albedo", material.albedo)} " +
                "${p("float roughness", material.roughness)} ${p("float ior", material.ior)}",
            coated(material.albedo, material.roughness, material.ior),
        )
        is Checker -> listOf(
            "#krt Material \"checker\" ${p("rgb even", material.even)} ${p("rgb odd", material.odd)} " +
                "${p("float cellsize", material.cellSize)} ${p("float roughness", material.roughness)} " +
                p("float ior", material.ior),
            coated((material.even + material.odd) * HALF, material.roughness, material.ior),
        )
        is Conductor -> conductor(material)
        else -> physical(material)
    }

    private fun physical(material: Material): List<String> = when (material) {
        is SoapBubble -> listOf(
            "#krt Material \"soapbubble\" ${p("float thickness", material.thickness)} " +
                p("float filmior", material.filmIor),
            "Material \"thindielectric\" ${p("float eta", material.filmIor)}",
        )
        is Iridescent -> listOf(
            "#krt Material \"iridescent\" ${p("rgb base", material.base)} " +
                "${p("float thickness", material.thickness)} ${p("float filmior", material.filmIor)} " +
                p("float roughness", material.roughness),
            coated(material.base, material.roughness, material.filmIor),
        )
        is Velvet -> listOf(
            "#krt Material \"velvet\" ${p("rgb base", material.base)} ${p("rgb sheen", material.sheen)} " +
                p("float roughness", material.roughness),
            "Material \"diffuse\" ${p("rgb reflectance", material.base)}",
        )
        is Patterned -> listOf(
            "#krt Material \"patterned\" ${pattern(material.pattern)} ${p("float roughness", material.roughness)} " +
                p("float bump", material.bump),
            coated(middle(material.pattern), material.roughness, Plastic.COAT_IOR),
        )
        else -> lights(material)
    }

    private fun lights(material: Material): List<String> = when (material) {
        is Emissive -> listOfNotNull(
            "ReverseOrientation".takeIf { material.emissionSide == EmissionSide.BACK },
            "AreaLightSource \"diffuse\" ${p("rgb L", material.emitColor)} ${p("float scale", material.intensity)}" +
                " \"bool twosided\" ${material.emissionSide == EmissionSide.BOTH}",
            BLACK,
        )
        is GlowingPattern -> listOf(
            "#krt AreaLightSource \"pattern\" ${pattern(material.pattern)} ${p("float intensity", material.intensity)}",
            "AreaLightSource \"diffuse\" ${p("rgb L", middle(material.pattern))} " +
                p("float scale", material.intensity) + " \"bool twosided\" true",
            BLACK,
        )
        Interface -> listOf("Material \"interface\"")
        else -> listOf("Material \"diffuse\" ${p("rgb reflectance", Color(HALF, HALF, HALF))}")
    }

    private fun dielectric(material: Dielectric): List<String> {
        val exact = material.density != 0.0 || material.tint != Color.ONE
        val krt = if (exact) {
            listOf(
                "#krt Material \"dielectric\" ${p("float ior", material.indexOfReflection)} " +
                    "${p("rgb tint", material.tint)} ${p("float density", material.density)} " +
                    "${p("float dispersion", material.dispersion)} ${p("float roughness", material.roughness)}",
            )
        } else {
            emptyList()
        }
        val eta = if (material.dispersion != 0.0) {
            val samples = (0..SPECTRUM_SAMPLES).map {
                Spectrum.MIN_NM + (Spectrum.MAX_NM - Spectrum.MIN_NM) * it / SPECTRUM_SAMPLES
            }
            "\"spectrum eta\" [${samples.joinToString(" ") { "${n(it)} ${n(material.indexAt(it))}" }}]"
        } else {
            p("float eta", material.indexOfReflection)
        }
        val roughness = if (material.roughness > 0) {
            " " + p("float roughness", PbrtValues.toPbrtRoughness(material.roughness))
        } else {
            ""
        }
        return krt + "Material \"dielectric\" $eta$roughness"
    }

    private fun conductor(material: Conductor): List<String> {
        val brushing = material.brushing?.let { " " + p("vector3 brushing", it) } ?: ""
        val krt = "#krt Material \"conductor\" ${p("rgb eta", material.eta)} ${p("rgb k", material.k)} " +
            "${p("float roughness", material.roughness)} ${p("float anisotropy", material.anisotropy)}$brushing"
        val metal = PBRT_METALS[Metals.entries.find { it.eta == material.eta && it.k == material.k }]
        val spectra = metal?.let { "\"spectrum eta\" \"metal-$it-eta\" \"spectrum k\" \"metal-$it-k\"" }
            ?: "${p("rgb eta", material.eta)} ${p("rgb k", material.k)}"
        val alphaX = material.facets.alphaX
        val alphaY = material.facets.alphaY
        return listOf(
            krt,
            "Material \"conductor\" $spectra ${p("float uroughness", alphaX * alphaX)} " +
                p("float vroughness", alphaY * alphaY),
        )
    }

    private fun coated(albedo: Color, roughness: Double, ior: Double): String =
        "Material \"coateddiffuse\" ${p("rgb reflectance", albedo)} " +
            "${p("float roughness", PbrtValues.toPbrtRoughness(roughness))} ${p("float eta", ior)}"

    private fun middle(pattern: Pattern): Color = (pattern.first + pattern.second) * HALF

    private fun pattern(pattern: Pattern): String =
        "\"string kind\" \"${pattern.kind.name.lowercase()}\" ${p("rgb first", pattern.first)} " +
            "${p("rgb second", pattern.second)} ${p("float scale", pattern.scale)} " +
            "${p("float turbulence", pattern.turbulence)} ${p("float frequency", pattern.frequency)} " +
            "\"integer octaves\" ${pattern.octaves}"
}

/** Numbers and parameters as pbrt-v4 text: exact, so reading them back gives the same doubles. */
object PbrtText {
    const val KRT_HEADER = "# A krt scene in pbrt-v4's format; #krt lines are krt's own, pbrt reads them as comments."

    fun n(x: Double): String = x.toString()

    fun v(p: krt.platform.math.Point3): String = "${n(p.x)} ${n(p.y)} ${n(p.z)}"

    fun p(declaration: String, value: Double): String = "\"$declaration\" ${n(value)}"

    fun p(declaration: String, value: krt.platform.math.Point3): String = "\"$declaration\" [${v(value)}]"
}
