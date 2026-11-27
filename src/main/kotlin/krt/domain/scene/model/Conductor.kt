package krt.domain.scene.model

import kotlin.random.Random
import krt.platform.math.Color
import krt.platform.math.Ray

/** Measured complex refraction indices of metals, at the wavelengths of the red, green and blue channels. */
@Suppress("MagicNumber") // measured data
enum class Metals(val eta: Color, val k: Color) {
    GOLD(Color(0.143, 0.374, 1.442), Color(3.983, 2.385, 1.603)),
    COPPER(Color(0.200, 0.924, 1.102), Color(3.912, 2.452, 2.142)),
    CHROME(Color(3.110, 3.180, 2.320), Color(3.310, 3.330, 3.130)),
    SILVER(Color(0.155, 0.117, 0.138), Color(4.828, 3.122, 2.147)),
    ALUMINIUM(Color(1.657, 0.880, 0.521), Color(9.224, 6.270, 4.837)),
}

/**
 * A metal whose colour comes from its complex refraction index [eta] + i[k] (Fresnel for conductors) and whose
 * gloss comes from GGX microfacets of [roughness]; [anisotropy] stretches the highlight into brushed circles
 * around the vertical axis (ADR 0013).
 */
class Conductor(val eta: Color, val k: Color, val roughness: Double, val anisotropy: Double = 0.0) : Material {

    constructor(metal: Metals, roughness: Double, anisotropy: Double = 0.0) :
        this(metal.eta, metal.k, roughness, anisotropy)

    val facets = Microfacets.of(roughness, anisotropy)

    override fun scatter(ray: Ray, hit: Hit, random: Random): Scattered? {
        val frame = Microfacets.frame(hit.normal)
        val outgoing = frame.local(-ray.direction.unit())
        val facet = facets.visibleNormal(outgoing, random)
        val incoming = facet * (2 * outgoing.dot(facet)) - outgoing
        if (outgoing.z <= 0 || incoming.z <= 0) return null
        val weight = ConductorFresnel.reflectance(outgoing.dot(facet), eta, k) * facets.shadowing(outgoing, incoming)
        return Scattered(ray.next(hit.p, frame.world(incoming)), weight)
    }
}
