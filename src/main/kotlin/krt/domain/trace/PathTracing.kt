package krt.domain.trace

import kotlin.math.PI
import kotlin.random.Random
import krt.domain.scene.model.Hit
import krt.domain.scene.model.Lobe
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
 */
object PathTracing {

    /**
     * With [SceneLook.motionBlur] the ray from the camera is sent at a random moment of the shutter, and the rays
     * that go on from it keep that moment (ADR 0013).
     */
    fun color(scene: TraceScene, ray: Ray, depth: Int, random: Random): Color {
        val timed = if (scene.look.motionBlur) {
            Ray(ray.origin, ray.direction, random.nextDouble(), ray.wavelength)
        } else {
            ray
        }
        return color(scene, timed, depth, random, true)
    }

    private fun color(scene: TraceScene, ray: Ray, depth: Int, random: Random, countEmission: Boolean): Color {
        if (depth <= 0) return Point3.ZERO

        val hitRecord = scene.hit(ray, T_MIN, Double.POSITIVE_INFINITY)
        return if (hitRecord != null) {
            val scattered = hitRecord.material.scatter(ray, hitRecord, random)
            val emitted = if (countEmission) hitRecord.material.emitted(0.0, 0.0, hitRecord.p) else Point3.ZERO
            when {
                scattered == null -> emitted
                scene.sampleLights && scattered.lobe != Lobe.SPECULAR ->
                    emitted + direct(scene, ray, hitRecord, scattered, random) +
                        scattered.attenuation * color(scene, scattered.ray, depth - 1, random, false)
                else -> emitted + scattered.attenuation * color(scene, scattered.ray, depth - 1, random, true)
            }
        } else {
            scene.sky.color(ray.direction)
        }
    }

    /** Light reaching [hit] straight from one sampled light, as the scattering surface or medium returns it. */
    private fun direct(scene: TraceScene, ray: Ray, hit: Hit, scattered: Scattered, random: Random): Color {
        val light = scene.lights.sample(hit.p, ray.time, random) ?: return Point3.ZERO
        val shadow = scene.hit(ray.next(hit.p, light.direction), T_MIN, light.distance * (1 - SHADOW_MARGIN))
        val response = when {
            shadow != null -> 0.0
            scattered.lobe == Lobe.VOLUME -> 1 / (4 * PI)
            else -> diffuse(maxOf(0.0, (scattered.normal ?: hit.normal).dot(light.direction)))
        }
        return light.emitted * scattered.attenuation * (response / light.pdf)
    }

    /**
     * Response of a diffuse bounce to light at [cosine] to the normal: BRDF × cosine. Diffuse bounces in krt go
     * from the normal to a random point in the unit ball around its tip (Lambert of the classic scene), whose
     * directions have the density 2 cos³ / π, so that is what the surface does with direct light too (ADR 0012).
     */
    private fun diffuse(cosine: Double): Double = 2 * cosine * cosine * cosine / PI

    /** Hits closer than this are self-intersections of the surface the ray starts on. */
    private const val T_MIN = 0.001

    /** Shadow rays stop short of the light so they do not hit the light itself. */
    private const val SHADOW_MARGIN = 1e-4
}
