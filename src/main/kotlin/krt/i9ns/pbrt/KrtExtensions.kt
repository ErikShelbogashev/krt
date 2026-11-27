package krt.i9ns.pbrt

import krt.domain.scene.model.Checker
import krt.domain.scene.model.Conductor
import krt.domain.scene.model.Dielectric
import krt.domain.scene.model.GlowingPattern
import krt.domain.scene.model.Interface
import krt.domain.scene.model.Iridescent
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Material
import krt.domain.scene.model.Metal
import krt.domain.scene.model.Metals
import krt.domain.scene.model.Patterned
import krt.domain.scene.model.Plastic
import krt.domain.scene.model.SoapBubble
import krt.domain.scene.model.Velvet
import krt.i9ns.pbrt.PbrtValues.color
import krt.platform.math.Color
import krt.platform.math.Point3

/**
 * krt's own materials and lights, which pbrt-v4 cannot express, from `#krt` lines (ADR 0016): a pure
 * transformation. A `#krt` directive wins over the pbrt one it stands before.
 */
object KrtExtensions {
    private val GREY = Color(0.5, 0.5, 0.5)
    private const val WATER_IOR = 1.33
    private const val VELVET_ROUGHNESS = 0.5

    /** A `#krt Material` directive. */
    fun material(d: PbrtDirective): MaterialSpec = MaterialSpec(
        when (val type = d.string(0)) {
            "lambert" -> Lambert(color(d, "albedo", GREY))
            "metal" -> Metal(color(d, "albedo", GREY), d.double("fuzz", 0.0))
            "dielectric" -> dielectric(d)
            "plastic" -> Plastic(color(d, "albedo", GREY), d.double("roughness", 0.0), ior(d))
            "checker" -> Checker(
                color(d, "even", Point3.ONE), color(d, "odd", Point3.ZERO), d.double("cellsize", 1.0),
                d.double("roughness", 0.0), ior(d),
            )
            "conductor" -> conductor(d)
            "soapbubble" -> SoapBubble(d.double("thickness", 0.0), d.double("filmior", WATER_IOR))
            "iridescent" -> Iridescent(
                color(d, "base", Point3.ZERO), d.double("thickness", 0.0), d.double("filmior", PbrtValues.DEFAULT_ETA),
                d.double("roughness", 0.0),
            )
            "velvet" -> Velvet(
                color(d, "base", GREY), color(d, "sheen", Point3.ONE), d.double("roughness", VELVET_ROUGHNESS),
            )
            "patterned" -> Patterned(PbrtValues.pattern(d), d.double("roughness", 0.0), d.double("bump", 0.0))
            "interface" -> Interface
            else -> d.fail("krt material $type unknown")
        },
    )

    /** A `#krt AreaLightSource "pattern"` directive: a glowing pattern. */
    fun light(d: PbrtDirective): Material =
        if (d.string(0) == "pattern") {
            GlowingPattern(PbrtValues.pattern(d), d.double("intensity", 1.0))
        } else {
            d.fail("krt light ${d.string(0)} unknown")
        }

    private fun ior(d: PbrtDirective): Double = d.double("ior", PbrtValues.DEFAULT_ETA)

    private fun dielectric(d: PbrtDirective): Dielectric = Dielectric(
        ior(d), color(d, "tint", Point3.ONE), d.double("density", 0.0), d.double("dispersion", 0.0),
        d.double("roughness", 0.0),
    )

    private fun conductor(d: PbrtDirective): Conductor {
        val metal = d.params["metal"]?.strings()?.firstOrNull()?.let { Metals.valueOf(it.uppercase()) }
        return Conductor(
            color(d, "eta", metal?.eta ?: Metals.SILVER.eta),
            color(d, "k", metal?.k ?: Metals.SILVER.k),
            d.double("roughness", 0.0),
            d.double("anisotropy", 0.0),
            PbrtValues.vector(d, "brushing"),
        )
    }
}
