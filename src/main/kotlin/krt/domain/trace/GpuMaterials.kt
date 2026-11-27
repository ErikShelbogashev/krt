package krt.domain.trace

import krt.domain.scene.model.Checker
import krt.domain.scene.model.Conductor
import krt.domain.scene.model.Dielectric
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.GlowingPattern
import krt.domain.scene.model.Iridescent
import krt.domain.scene.model.Isotropic
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Material
import krt.domain.scene.model.Metal
import krt.domain.scene.model.Pattern
import krt.domain.scene.model.Patterned
import krt.domain.scene.model.Plastic
import krt.domain.scene.model.SoapBubble
import krt.domain.scene.model.Velvet
import krt.domain.trace.GpuScenePacking.BUBBLE
import krt.domain.trace.GpuScenePacking.CHECKER
import krt.domain.trace.GpuScenePacking.CONDUCTOR
import krt.domain.trace.GpuScenePacking.DIELECTRIC
import krt.domain.trace.GpuScenePacking.EMISSIVE
import krt.domain.trace.GpuScenePacking.GLOWING
import krt.domain.trace.GpuScenePacking.IRIDESCENT
import krt.domain.trace.GpuScenePacking.ISOTROPIC
import krt.domain.trace.GpuScenePacking.LAMBERT
import krt.domain.trace.GpuScenePacking.METAL
import krt.domain.trace.GpuScenePacking.PATTERNED
import krt.domain.trace.GpuScenePacking.PLASTIC
import krt.domain.trace.GpuScenePacking.VELVET
import krt.platform.math.Point3

/** The materials of a [GpuScene]: a code and parameters per material, mirrored by the shader's decoding. */
object GpuMaterials {

    /**
     * The values of [material]: code; colour r, g, b; parameters 1 and 2; second colour r, g, b; parameters 3 to 5.
     * - Lambert: albedo;
     * - Metal: albedo, fuzz;
     * - Dielectric: absorption per unit of distance (Beer–Lambert), refraction index, dispersion, none, GGX α of
     *   frosted glass (0 for clear glass);
     * - Emissive: emitted light;
     * - Plastic: albedo, coat roughness, coat refraction index;
     * - Checker: even cells, coat roughness, coat refraction index, odd cells, cell size;
     * - Isotropic (the particles of fog): albedo;
     * - Soap bubble: none, film thickness in nm, film refraction index;
     * - Iridescent: base, film thickness in nm, film refraction index, none, roughness;
     * - Conductor: η, GGX α along and across the tangent, k;
     * - Velvet: base, roughness, chance of the diffuse bounce, sheen;
     * - Patterned: first colour, coat roughness, pattern scale, second colour, bump, pattern kind, turbulence;
     * - Glowing pattern: first colour, intensity, pattern scale, second colour, none, pattern kind, turbulence.
     */
    fun values(material: Material): DoubleArray = surface(material) ?: physical(material)
        ?: values(LAMBERT, Point3(UNKNOWN_GREY, UNKNOWN_GREY, UNKNOWN_GREY))

    private fun surface(material: Material): DoubleArray? = when (material) {
        is Lambert -> values(LAMBERT, material.albedo)
        is Metal -> values(METAL, material.albedo, material.fuzz)
        is Dielectric -> values(
            DIELECTRIC, material.absorption, material.indexOfReflection, material.dispersion,
            third = if (material.roughness > 0) material.facets.alphaX else 0.0,
        )
        is Conductor -> values(
            CONDUCTOR, material.eta, material.facets.alphaX, material.facets.alphaY, odd = material.k,
        )
        is Emissive -> values(EMISSIVE, material.emitColor * material.intensity)
        is Plastic -> values(PLASTIC, material.albedo, material.roughness, material.ior)
        is Checker -> values(
            CHECKER, material.even, material.roughness, material.ior, odd = material.odd, third = material.cellSize,
        )
        else -> null
    }

    private fun physical(material: Material): DoubleArray? = when (material) {
        is Isotropic -> values(ISOTROPIC, material.albedo)
        is Velvet -> values(VELVET, material.base, material.roughness, material.diffuseChance, odd = material.sheen)
        is Patterned -> patterned(PATTERNED, material.pattern, material.roughness, material.bump)
        is GlowingPattern -> patterned(GLOWING, material.pattern, material.intensity, 0.0)
        is SoapBubble -> values(BUBBLE, Point3.ZERO, material.thickness, material.filmIor)
        is Iridescent -> values(
            IRIDESCENT, material.base, material.thickness, material.filmIor, third = material.roughness,
        )
        else -> null
    }

    @Suppress("LongParameterList")
    private fun values(
        code: Int,
        color: Point3,
        first: Double = 0.0,
        second: Double = 0.0,
        odd: Point3 = Point3.ZERO,
        third: Double = 0.0,
        fourth: Double = 0.0,
        fifth: Double = 0.0,
    ): DoubleArray = doubleArrayOf(
        code.toDouble(), color.x, color.y, color.z, first, second, odd.x, odd.y, odd.z, third, fourth, fifth,
    )

    /** A material coloured by [pattern]: its two colours, scale, kind and turbulence around [first] and [third]. */
    private fun patterned(code: Int, pattern: Pattern, first: Double, third: Double): DoubleArray = values(
        code, pattern.first, first, pattern.scale, pattern.second, third,
        pattern.kind.ordinal.toDouble(), pattern.turbulence,
    )


    /** Colour of a material the shader does not know. */
    private const val UNKNOWN_GREY = 0.5
}
