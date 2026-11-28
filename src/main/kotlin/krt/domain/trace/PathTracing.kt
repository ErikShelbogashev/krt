package krt.domain.trace

import kotlin.math.PI
import kotlin.random.Random
import krt.domain.scene.model.FreeFlight
import krt.domain.scene.model.HenyeyGreenstein
import krt.domain.scene.model.Hit
import krt.domain.scene.model.Interface
import krt.domain.scene.model.Lobe
import krt.domain.scene.model.Medium
import krt.domain.scene.model.Scattered
import krt.domain.scene.model.SceneLook
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

/**
 * Colour seen along a ray: the path tracing recursion of the kernel (ADR 0008).
 *
 * With [TraceScene.sampleLights] on, every diffuse or volume scattering also samples one light directly (next event
 * estimation, ADR 0012), and the path that goes on from it no longer counts the light it hits, which that sample
 * already took; mirror and glass bounces still see lights along their rays. With it off, the estimator is the plain
 * one of before, sample for sample.
 *
 * A path remembers the medium it travels in (ADR 0014): it enters the inside medium of a surface it passes through
 * from the front and the outside one from the back, and between surfaces it may scatter off the medium's particles.
 * Surfaces of [Interface] only bound media: crossing one does not count as a bounce.
 */
object PathTracing {

    /** Where a path is: the medium around it, whether a solid surface closes that medium, whether it sees lights. */
    private class State(val medium: Medium?, val enclosed: Boolean, val countEmission: Boolean, val crossings: Int = 0)

    private val CLEAR = State(null, enclosed = false, countEmission = true)
    private val CLEAR_SAMPLED = State(null, enclosed = false, countEmission = false)

    /**
     * With [SceneLook.motionBlur] the ray from the camera is sent at a random moment of the shutter, and the rays
     * that go on from it keep that moment (ADR 0013). A ray from inside a bank of fog starts in its medium.
     */
    fun color(scene: TraceScene, ray: Ray, depth: Int, random: Random): Color {
        val timed = if (scene.look.motionBlur) {
            Ray(ray.origin, ray.direction, random.nextDouble(), ray.wavelength)
        } else {
            ray
        }
        val start = scene.mediumAt(ray.origin)?.let { State(it, enclosed = false, countEmission = true) } ?: CLEAR
        return color(scene, timed, depth, random, start)
    }

    private fun color(scene: TraceScene, ray: Ray, depth: Int, random: Random, state: State): Color {
        if (depth <= 0) return Point3.ZERO
        val hit = scene.hit(ray, T_MIN, Double.POSITIVE_INFINITY)
        return if (state.medium == null) {
            surface(scene, ray, hit, depth, random, state)
        } else {
            throughMedium(scene, ray, hit, depth, random, state)
        }
    }

    /** In a medium: a particle before the surface, or the surface, each weighed by [FreeFlight]. */
    @Suppress("LongParameterList")
    private fun throughMedium(scene: TraceScene, ray: Ray, hit: Hit?, depth: Int, random: Random, state: State): Color {
        val medium = requireNotNull(state.medium)
        val speed = ray.direction.length()
        val flight = FreeFlight.distance(medium, random.nextDouble(), random.nextDouble())
        val surfaceDistance = (hit?.t ?: Double.POSITIVE_INFINITY) * speed
        return if (flight < surfaceDistance) {
            particle(scene, ray, flight / speed, depth, random, state)
        } else {
            FreeFlight.passWeight(medium, surfaceDistance) * surface(scene, ray, hit, depth, random, state)
        }
    }

    /** Light from a surface the ray hits, or from the sky when it hits none. */
    @Suppress("LongParameterList")
    private fun surface(scene: TraceScene, ray: Ray, hit: Hit?, depth: Int, random: Random, state: State): Color {
        if (hit == null) return scene.sky.color(ray.direction)
        val scattered = hit.material.scatter(ray, hit, random)
        val counted = (state.countEmission || !hit.lightSampled) && hit.material.emissionSide.visible(hit.isFrontFace)
        val emitted = if (counted) hit.material.emitted(0.0, 0.0, hit.p) else Point3.ZERO
        return if (scattered == null) emitted else emitted + bounce(scene, ray, hit, scattered, depth, random, state)
    }

    /** Light that comes back along [scattered]: direct from a sampled light, and from the rest of the path. */
    @Suppress("LongParameterList")
    private fun bounce(
        scene: TraceScene,
        ray: Ray,
        hit: Hit,
        scattered: Scattered,
        depth: Int,
        random: Random,
        state: State,
    ): Color {
        val crossing = hit.material === Interface
        if (crossing && state.crossings == MAX_PATH_CROSSINGS) return Point3.ZERO
        val sampled = depth > 1 && scene.sampleLights && scattered.lobe.samplesLights
        val direct = if (sampled) {
            direct(scene, ray, hit.p, hit.normal, scattered, state.medium, random)
        } else {
            Point3.ZERO
        }
        val next = after(hit, scattered, state, if (crossing) state.countEmission else !sampled)
        val bounces = if (crossing) depth else depth - 1
        return direct + scattered.attenuation * color(scene, scattered.ray, bounces, random, next)
    }

    /**
     * A scattering off a particle of the medium at [t] along the ray, tinted by the albedo: in every direction, or
     * by the medium's phase function when it scatters onwards (its g is not 0); drawn in proportion to it either way.
     */
    private fun particle(scene: TraceScene, ray: Ray, t: Double, depth: Int, random: Random, state: State): Color {
        val medium = requireNotNull(state.medium)
        val p = ray.at(t)
        val lobe = if (state.enclosed) Lobe.ENCLOSED_VOLUME else Lobe.VOLUME
        val direction = if (medium.g == 0.0) {
            Point3.randomInUnitSphere(random).unit()
        } else {
            HenyeyGreenstein.sample(ray.direction.unit(), medium.g, random.nextDouble(), random.nextDouble())
        }
        val scattered = Scattered(ray.next(p, direction), Point3.ONE, lobe)
        val sampled = depth > 1 && scene.sampleLights && lobe.samplesLights
        val direct = if (sampled) direct(scene, ray, p, Point3.ZERO, scattered, medium, random) else Point3.ZERO
        val next = State(medium, state.enclosed, !sampled, state.crossings)
        val weight = FreeFlight.scatterWeight(medium, t * ray.direction.length())
        return weight * (direct + color(scene, scattered.ray, depth - 1, random, next))
    }

    /** The state of the path after [scattered] left [hit]: through the surface it goes into the medium beyond. */
    private fun after(hit: Hit, scattered: Scattered, state: State, countEmission: Boolean): State {
        val media = hit.media
        val through = media != null && scattered.ray.direction.dot(hit.normal) < 0
        val crossings = state.crossings + if (hit.material === Interface) 1 else 0
        return when {
            !through && state.medium == null && crossings == 0 -> if (countEmission) CLEAR else CLEAR_SAMPLED
            !through -> State(state.medium, state.enclosed, countEmission, crossings)
            hit.isFrontFace -> State(media.inside, hit.material !== Interface, countEmission, crossings)
            else -> State(media.outside, enclosed = false, countEmission = countEmission, crossings = crossings)
        }
    }

    /** Light reaching [p] straight from one sampled light, as the scattering surface or medium returns it. */
    @Suppress("LongParameterList")
    private fun direct(
        scene: TraceScene,
        ray: Ray,
        p: Point3,
        normal: Point3,
        scattered: Scattered,
        medium: Medium?,
        random: Random,
    ): Color {
        val light = scene.lights.sample(p, ray.time, random) ?: return Point3.ZERO
        val response = when (scattered.lobe) {
            Lobe.VOLUME -> HenyeyGreenstein.density(ray.direction.unit().dot(light.direction), medium?.g ?: 0.0)
            else -> diffuse(maxOf(0.0, (scattered.normal ?: normal).dot(light.direction)))
        }
        val shadow = Ray(p, light.direction, ray.time, ray.wavelength)
        val transmittance = if (response == 0.0) {
            Point3.ZERO
        } else {
            transmittance(scene, shadow, light.distance * (1 - SHADOW_MARGIN), medium)
        }
        return light.emitted * scattered.attenuation * transmittance * (response / light.pdf)
    }

    /**
     * Share of light that comes along [shadow] over [distance] (unit direction): none past a solid surface; through
     * media and the [Interface] surfaces that bound them, what the media let through.
     */
    private fun transmittance(scene: TraceScene, shadow: Ray, distance: Double, start: Medium?): Color {
        var ray = shadow
        var remaining = distance
        var medium = start
        var share = Point3.ONE
        var crossings = 0
        var result: Color? = null
        while (result == null) {
            val hit = scene.hit(ray, T_MIN, remaining)
            result = when {
                hit == null -> share * (medium?.transmittance(remaining) ?: Point3.ONE)
                hit.material !== Interface || ++crossings > MAX_CROSSINGS -> Point3.ZERO
                else -> {
                    share *= medium?.transmittance(hit.t) ?: Point3.ONE
                    medium = if (hit.isFrontFace) hit.media?.inside else hit.media?.outside
                    remaining -= hit.t
                    ray = ray.next(hit.p, ray.direction)
                    null
                }
            }
        }
        return result
    }

    /** The Lambertian BRDF times the projected solid-angle factor, for unit reflectance. */
    private fun diffuse(cosine: Double): Double = cosine / PI

    /** Hits closer than this are self-intersections of the surface the ray starts on. */
    private const val T_MIN = 0.001

    /** Shadow rays stop short of the light so they do not hit the light itself. */
    private const val SHADOW_MARGIN = 1e-4

    /** Transparent interfaces on a camera path, in addition to its scattering depth. Shared with SkSL. */
    const val MAX_PATH_CROSSINGS = 64

    /** Medium boundaries a shadow ray passes at most before it counts as blocked. */
    const val MAX_CROSSINGS = 8
}
