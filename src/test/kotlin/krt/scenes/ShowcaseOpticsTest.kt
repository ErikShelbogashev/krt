package krt.scenes

import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import krt.domain.camera.Camera
import krt.domain.render.FrameSampling
import krt.domain.render.RayTracer
import krt.domain.scene.World
import krt.domain.scene.model.DemoScene
import krt.domain.scene.model.Dielectric
import krt.domain.scene.model.Fog
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Meshes
import krt.domain.scene.model.Pose
import krt.domain.scene.model.SceneDescription
import krt.domain.scene.model.Traceable
import krt.domain.scene.model.Triangle
import krt.i9ns.scenefile.SceneLibrary
import krt.platform.math.Color
import krt.platform.math.Point3

/** Numerical image probes on the packaged scenes. Coordinates and optical controls: docs/scene-optics.md. */
class ShowcaseOpticsTest {
    private val scenes = SceneLibrary()

    @Test
    fun `the slit seen through the prism separates into bright red green and blue bands`() {
        val prism = scenes.load(DemoScene.PRISM)
        val dispersed = View(prism, prism.objects, depth = 3)
        // Predicted horizontal positions for three transmitted wavelengths (650, 532 and 450 nm).
        val red = dispersed.patch(159..161, 82..87)
        val green = dispersed.patch(165..167, 82..87)
        val blue = dispersed.patch(174..177, 82..87)
        assertTrue(red.x > 3 * max(red.y, red.z), "red slit image: $red")
        assertTrue(green.y > 3 * max(green.x, green.z), "green slit image: $green")
        assertTrue(blue.z > 3 * max(blue.x, blue.y), "blue slit image: $blue")
        // The three bands must all be visible. Tiny coloured reflections alone also have high saturation.
        assertTrue(minOf(red.x, green.y, blue.z) > 0.25, "linear signals: $red, $green, $blue")
    }

    @Test
    fun `the same slit stays bright with coincident channel centres at constant refractive index`() {
        val prism = scenes.load(DemoScene.PRISM)
        val constantIndex = prism.objects.map { obj ->
            val glass = (obj as? Triangle)?.material as? Dielectric
            if (obj is Triangle && glass != null) {
                Triangle(obj.v0, obj.v1, obj.v2, Dielectric(glass.indexOfReflection), obj.id, obj.normals, obj.media)
            } else obj
        }
        val view = View(prism, constantIndex, depth = 3)
        var energy = Point3.ZERO
        var moment = Point3.ZERO
        for (x in 146..180) {
            val column = view.patch(x..x, 82..87)
            energy += column
            moment += column * x.toDouble()
        }
        assertTrue(minOf(energy.x, energy.y, energy.z) > 1.0, "slit without dispersion: $energy")
        val red = moment.x / energy.x
        val green = moment.y / energy.y
        val blue = moment.z / energy.z
        assertTrue(max(abs(blue - red), abs(green - red)) < 0.25, "centres: $red $green $blue")
    }

    @Test
    fun `closing the slit removes the spectrum even though the lamp is still on`() {
        val prism = scenes.load(DemoScene.PRISM)
        // An opaque plate covers the 0.04-unit slit from outside the lamp house.
        val shutter = Meshes.box(
            Pose(Point3(-1.55, 0.95, 0.0)), Point3(0.01, 0.96, 0.03), Lambert(Point3.ZERO),
        )
        val closed = View(prism, prism.objects + shutter, depth = 3)
        val rgb = closed.patch(159..177, 82..87)
        assertTrue(maxOf(rgb.x, rgb.y, rgb.z) < 0.01, "unlit spectrum: $rgb")
    }

    @Test
    fun `forward scattering brightens the tunnel shaft more than the adjacent shadow`() {
        val temple = scenes.load(DemoScene.TEMPLE)
        val haze = temple.objects.filterIsInstance<Fog>().single()
        val isotropic = Fog(haze.center, haze.radius, haze.medium.copy(g = 0.0), haze.id, haze.outside)
        val background = temple.objects - haze
        val clear = View(temple, background, depth = 4, samples = 512)
        val foggy = View(temple, temple.objects, depth = 4, samples = 512)
        val isotropicView = View(temple, background + isotropic, depth = 4, samples = 512)
        val shaft = 160..163
        val shadow = 194..197
        val row = 96..99
        val clearShaft = clear.patch(shaft, row).dot(LUMA)
        val addedLight = foggy.patch(shaft, row).dot(LUMA) - clearShaft
        val addedShadow = (foggy.patch(shadow, row) - clear.patch(shadow, row)).dot(LUMA)
        val isotropicLight = isotropicView.patch(shaft, row).dot(LUMA) - clearShaft
        assertTrue(addedLight > 0.1, "light added by the medium: $addedLight")
        assertTrue(addedLight > 2 * addedShadow, "shaft $addedLight vs shadow $addedShadow")
        assertTrue(addedLight > 2 * isotropicLight, "forward $addedLight vs isotropic $isotropicLight")
    }

    private class View(
        scene: SceneDescription, objects: List<Traceable>, depth: Int, private val samples: Int = 128,
    ) {
        private val camera = Camera.default().apply {
            imageWidth = WIDTH
            imageHeight = HEIGHT
            resetView(scene.look)
        }
        private val tracer = RayTracer(World(objects, scene.look), depth)

        /** Mean linear RGB of the region, before clamping or the display transfer function. */
        fun patch(columns: IntRange, rows: IntRange): Color {
            var sum = Point3.ZERO
            for (y in rows) for (x in columns) {
                val random = Random(y * WIDTH + x)
                val pixel = FrameSampling.Pixel(x, HEIGHT - 1 - y)
                repeat(samples) { sum += FrameSampling.sampleOnce(pixel, camera, tracer, random) }
            }
            return sum / (columns.count() * rows.count() * samples).toDouble()
        }
    }

    private companion object {
        const val WIDTH = 320
        const val HEIGHT = 180
        val LUMA = Color(0.2126, 0.7152, 0.0722)
    }
}
