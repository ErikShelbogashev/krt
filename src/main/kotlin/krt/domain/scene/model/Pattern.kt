package krt.domain.scene.model

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

/** The procedural patterns (ADR 0013). */
enum class PatternKind {
    /** Fractal noise, soft blotches of the two colours. */
    NOISE,

    /** Veins of [Pattern.second] winding through [Pattern.first], like marble. */
    MARBLE,

    /**
     * Wood cut along the trunk, the trunk along the x axis: the rings of growth meet a floor or a board as long
     * stripes of thin dark latewood across pale earlywood, wavy along the grain, over fine fibres.
     */
    WOOD,

    /** Puffs of [Pattern.second] over [Pattern.first] with clear gaps, like clouds. */
    CLOUDS,

    /** Lines of [Pattern.second] [Pattern.frequency] of a cell wide on a grid of unit cells, like a screen. */
    GRID,

    /** Soft horizontal stripes of [Pattern.second], [Pattern.frequency] to a unit, like pulsing lights. */
    STRIPES,
}

/**
 * A procedural pattern in space: [kind] blends [first] and [second] by a value from noise at the point times
 * [scale]; [turbulence] says how strongly noise bends the veins of marble and the rings of wood; [frequency] is
 * the veins of marble and the rings of wood to a unit, the width of grid lines and the stripes to a unit;
 * [octaves], those of the noise in noise, marble and clouds.
 */
data class Pattern(
    val kind: PatternKind,
    val first: Color,
    val second: Color,
    val scale: Double = 1.0,
    val turbulence: Double = DEFAULT_TURBULENCE,
    val frequency: Double = 1.0,
    val octaves: Int = Noise.OCTAVES,
) {
    fun colorAt(p: Point3): Color {
        val t = valueAt(p)
        return first * (1 - t) + second * t
    }

    /** The blend at [p], in [0, 1]: 0 for [first], 1 for [second]; also the height for bumps. */
    fun valueAt(p: Point3): Double {
        val q = p * scale
        val value = when (kind) {
            PatternKind.NOISE -> HALF + HALF * Noise.fbm(q, octaves)
            PatternKind.MARBLE -> marble(q)
            PatternKind.WOOD -> wood(q)
            PatternKind.CLOUDS -> smoothstep(CLOUD_LOW, CLOUD_HIGH, HALF + HALF * Noise.fbm(q, octaves))
            PatternKind.GRID -> grid(q)
            PatternKind.STRIPES -> smoothstep(STRIPE_LOW, STRIPE_HIGH, HALF + HALF * sin(2 * PI * frequency * q.y))
        }
        return value.coerceIn(0.0, 1.0)
    }

    private fun marble(q: Point3): Double {
        val veins = q.x * MARBLE_FREQUENCY * frequency + turbulence * MARBLE_SPREAD * Noise.turbulence(q, octaves)
        val wave = HALF + HALF * sin(veins)
        return 1 - sqrt(wave)
    }

    private fun wood(q: Point3): Double {
        val along = Point3(q.x * GRAIN_STRETCH, q.y * GRAIN_WAVE, q.z * GRAIN_WAVE)
        val rings = sqrt(q.y * q.y + q.z * q.z) * frequency + turbulence * Noise.perlin(along)
        val ring = rings - floor(rings)
        val latewood = smoothstep(LATEWOOD_START, LATEWOOD_PEAK, ring) * (1 - smoothstep(LATEWOOD_PEAK, 1.0, ring))
        val fibres = HALF + HALF * Noise.perlin(Point3(q.x * FIBRE_STRETCH, q.y * FIBRES, q.z * FIBRES))
        return LATEWOOD_SHARE * latewood + (1 - LATEWOOD_SHARE) * fibres
    }

    /** 1 on the lines between unit cells, [frequency] of a cell wide, along any of the three axes. */
    private fun grid(q: Point3): Double {
        val onLine = listOf(q.x, q.y, q.z).any { it - floor(it) < frequency }
        return if (onLine) 1.0 else 0.0
    }

    private fun smoothstep(low: Double, high: Double, x: Double): Double {
        val t = ((x - low) / (high - low)).coerceIn(0.0, 1.0)
        return t * t * (SMOOTH_A - SMOOTH_B * t)
    }

    companion object {
        const val DEFAULT_TURBULENCE = 1.0
        private const val HALF = 0.5
        private const val SMOOTH_A = 3.0
        private const val SMOOTH_B = 2.0
        private const val CLOUD_LOW = 0.45
        private const val CLOUD_HIGH = 0.75
        private const val STRIPE_LOW = 0.35
        private const val STRIPE_HIGH = 0.65
        private const val MARBLE_FREQUENCY = 2 * PI
        private const val MARBLE_SPREAD = 4.0
        private const val GRAIN_STRETCH = 0.25
        private const val GRAIN_WAVE = 2.0
        private const val LATEWOOD_START = 0.72
        private const val LATEWOOD_PEAK = 0.9
        private const val LATEWOOD_SHARE = 0.75
        private const val FIBRE_STRETCH = 0.5
        private const val FIBRES = 24.0
    }
}

/**
 * A polished surface coloured by a [pattern]: a diffuse base under a clear coat of [roughness], as [Plastic].
 * With [bump] above 0 the pattern also raises the surface: the normal tilts along the slope of [Pattern.valueAt]
 * times [bump], so veins and rings catch the light (bump mapping, ADR 0013).
 */
class Patterned(val pattern: Pattern, val roughness: Double, val bump: Double = 0.0) : Material {
    override fun scatter(ray: Ray, hit: Hit, random: Random): Scattered? {
        val surface = if (bump > 0) hit.copy(normal = Bump.normal(pattern, hit.p, hit.normal, bump)) else hit
        return ClearCoat.scatter(ray, surface, pattern.colorAt(hit.p), roughness, Plastic.COAT_IOR, random)
            ?.copy(normal = surface.normal)
    }
}

/** The normal of a surface raised by a pattern. */
object Bump {
    /** Step of the finite differences, in scene units divided by the pattern's scale. */
    private const val STEP = 1e-3

    /** [normal] tilted against the slope of [Pattern.valueAt] × [strength] along the surface at [p]. */
    fun normal(pattern: Pattern, p: Point3, normal: Point3, strength: Double): Point3 {
        val step = STEP / pattern.scale
        val here = pattern.valueAt(p)
        val gradient = Point3(
            pattern.valueAt(p + Point3(step, 0.0, 0.0)) - here,
            pattern.valueAt(p + Point3(0.0, step, 0.0)) - here,
            pattern.valueAt(p + Point3(0.0, 0.0, step)) - here,
        ) / step
        val along = gradient - normal * gradient.dot(normal)
        return (normal - along * (strength / pattern.scale)).unit()
    }
}
