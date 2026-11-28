package krt.domain.trace

import krt.domain.scene.model.Checker
import krt.domain.scene.model.Conductor
import krt.domain.scene.model.Dielectric
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.GlowingPattern
import krt.domain.scene.model.Iridescent
import krt.domain.scene.model.Interface
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
import krt.domain.trace.GpuScenePacking.INTERFACE
import krt.domain.trace.GpuScenePacking.LAMBERT
import krt.domain.trace.GpuScenePacking.METAL
import krt.domain.trace.GpuScenePacking.PATTERNED
import krt.domain.trace.GpuScenePacking.PLASTIC
import krt.domain.trace.GpuScenePacking.VELVET
import krt.platform.math.Point3

/** The materials of a [GpuScene]: a code and parameters per material, mirrored by the shader's decoding. */
object GpuMaterials {

    /**
     * The values of [material]: code; colour r, g, b; parameters 1 and 2; second colour r, g, b; parameters 3 to 7.
     * - Lambert: albedo;
     * - Metal: albedo, fuzz;
     * - Dielectric: absorption per unit of distance (Beer–Lambert), refraction index, dispersion, none, GGX α of
     *   frosted glass (0 for clear glass);
     * - Emissive: emitted light, side (0 both, +1 front, -1 back);
     * - Plastic: albedo, coat roughness, coat refraction index;
     * - Checker: even cells, coat roughness, coat refraction index, odd cells, cell size;
     * - Interface (the edge of a medium, which light crosses unchanged): nothing;
     * - Soap bubble: none, film thickness in nm, film refraction index;
     * - Iridescent: base, film thickness in nm, film refraction index, none, roughness;
     * - Conductor: η, GGX α along and across the tangent, k, the direction of brushing (0 for circles);
     * - Velvet: base, roughness, chance of the diffuse bounce, sheen;
     * - Patterned: first colour, coat roughness, pattern scale, second colour, bump, pattern kind, turbulence,
     *   frequency, octaves;
     * - Glowing pattern: first emitted RGB, none, pattern scale, second emitted RGB, none, pattern kind, turbulence,
     *   frequency, octaves.
     */
    fun values(material: Material): DoubleArray = surface(material) ?: physical(material)
        ?: values(LAMBERT, Point3(UNKNOWN_GREY, UNKNOWN_GREY, UNKNOWN_GREY))

    private fun surface(material: Material): DoubleArray? = when (material) {
        is Lambert -> values(LAMBERT, material.albedo)
        is Metal -> values(METAL, material.albedo, material.fuzz)
        is Dielectric -> values(
            DIELECTRIC, material.absorption, material.indexOfReflection, material.dispersion,
            third = if (material.roughness > 0) maxOf(material.facets.alphaX, MIN_FROSTED_ALPHA) else 0.0,
        )
        is Conductor -> values(
            CONDUCTOR, material.eta, material.facets.alphaX, material.facets.alphaY, odd = material.k,
            third = material.brushing?.x ?: 0.0, fourth = material.brushing?.y ?: 0.0,
            fifth = material.brushing?.z ?: 0.0,
        )
        is Emissive -> values(EMISSIVE, material.emitColor * material.intensity, material.emissionSide.sign)
        is Plastic -> values(PLASTIC, material.albedo, material.roughness, material.ior)
        is Checker -> values(
            CHECKER, material.even, material.roughness, material.ior, odd = material.odd, third = material.cellSize,
        )
        else -> null
    }

    private fun physical(material: Material): DoubleArray? = when (material) {
        is Interface -> values(INTERFACE, Point3.ZERO)
        is Velvet -> values(VELVET, material.base, material.roughness, material.diffuseChance, odd = material.sheen)
        is Patterned -> patterned(PATTERNED, material.pattern, material.roughness, material.bump)
        is GlowingPattern -> glowing(material)
        is SoapBubble -> values(BUBBLE, Point3.ZERO, material.thickness, material.filmIor)
        is Iridescent -> values(
            IRIDESCENT, material.base, material.thickness, material.filmIor, third = material.roughness,
        )
        else -> null
    }

    /** Linear emission commutes with interpolating the pattern. Bake intensity before quantizing either endpoint. */
    private fun glowing(material: GlowingPattern): DoubleArray {
        val pattern = material.pattern
        val emitted = pattern.copy(
            first = pattern.first * material.intensity, second = pattern.second * material.intensity,
        )
        return patterned(GLOWING, emitted, 0.0, 0.0)
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
        rest: DoubleArray = DoubleArray(2),
    ): DoubleArray = doubleArrayOf(
        code.toDouble(), color.x, color.y, color.z, first, second, odd.x, odd.y, odd.z, third, fourth, fifth,
    ) + rest

    /**
     * A material coloured by [pattern]: its two colours, scale, kind, turbulence, frequency and octaves around
     * [first] and [third].
     */
    private fun patterned(code: Int, pattern: Pattern, first: Double, third: Double): DoubleArray = values(
        code, pattern.first, first, pattern.scale, pattern.second, third,
        pattern.kind.ordinal.toDouble(), pattern.turbulence,
        doubleArrayOf(pattern.frequency, pattern.octaves.toDouble()),
    )


    /**
     * GGX α of frosted glass at least: two steps of the fixed point (about 4.9e-4 each), so glass with a little
     * roughness does not round to clear glass, which the shader tells by α = 0.
     */
    private const val MIN_FROSTED_ALPHA = 1e-3

    /** Colour of a material the shader does not know. */
    private const val UNKNOWN_GREY = 0.5
}
