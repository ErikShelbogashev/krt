package krt.domain.render

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import krt.domain.camera.Camera
import krt.domain.scene.World
import krt.domain.scene.model.Checker
import krt.domain.scene.model.Conductor
import krt.domain.scene.model.Dielectric
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.Fog
import krt.domain.scene.model.Fractal
import krt.domain.scene.model.FractalKind
import krt.domain.scene.model.GlowingPattern
import krt.domain.scene.model.Iridescent
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Medium
import krt.domain.scene.model.MediumInterface
import krt.domain.scene.model.Meshes
import krt.domain.scene.model.Metals
import krt.domain.scene.model.Metal
import krt.domain.scene.model.Pattern
import krt.domain.scene.model.PatternKind
import krt.domain.scene.model.Patterned
import krt.domain.scene.model.Plastic
import krt.domain.scene.model.Pose
import krt.domain.scene.model.SceneLook
import krt.domain.scene.model.Sky
import krt.domain.scene.model.SoapBubble
import krt.domain.scene.model.Translucent
import krt.domain.scene.model.Velvet
import krt.domain.scene.model.Sphere
import krt.domain.scene.model.Triangle
import krt.domain.trace.GpuScenePacking
import krt.platform.awt.toAWTColor
import krt.platform.math.Color
import krt.platform.math.Point3
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface

/**
 * The GPU shader runs here on Skia's CPU raster backend (ADR 0009). It has its own random numbers,
 * so it is compared with the CPU frame of the same scene by brightness, not pixel by pixel.
 */
class GpuTracerTest {

    private val world = World(
        listOf(
            Sphere(Point3(0.0, -1000.0, 0.0), 1000.0, Checker(Color(0.8, 0.8, 0.8), Color(0.2, 0.2, 0.2), 1.0, 0.1)),
            Sphere(Point3(0.0, 1.0, 0.0), 1.0, Dielectric(1.5)),
            Sphere(Point3(-2.5, 1.0, 0.0), 1.0, Lambert(Color(0.4, 0.2, 0.1))),
            Sphere(Point3(2.5, 1.0, 0.0), 1.0, Metal(Color(0.7, 0.6, 0.5), 0.05)),
            Sphere(Point3(0.0, 1.0, 2.5), 0.6, Emissive(Color(0.1, 0.7, 0.4), 2.0)),
            Triangle(Point3(-6.0, 0.0, -3.0), Point3(6.0, 0.0, -3.0), Point3(0.0, 5.0, -3.0), Lambert(WALL)),
            Sphere(Point3(-1.5, 0.5, 1.5), 0.5, Plastic(Color(0.8, 0.1, 0.1), 0.05)),
            Sphere(Point3(1.5, 0.5, 1.5), 0.5, Dielectric(1.5, Color(0.2, 0.6, 1.0), 1.5)),
        ),
    )

    /** A night scene lit by a lamp and a glowing panel, with a sphere of fog around the lamp. */
    private val litFog = World(
        listOf(
            Sphere(Point3(0.0, -1000.0, 0.0), 1000.0, Lambert(Color(0.7, 0.7, 0.7))),
            Sphere(Point3(0.0, 2.0, 0.0), 0.3, Emissive(Color(1.0, 0.85, 0.6), 12.0)),
            Triangle(Point3(-4.0, 0.0, -3.0), Point3(4.0, 0.0, -3.0), Point3(0.0, 4.0, -3.0), Emissive(WALL, 0.6)),
            Sphere(Point3(-2.0, 0.8, 1.0), 0.8, Plastic(Color(0.8, 0.1, 0.1), 0.05)),
            Sphere(Point3(2.0, 0.8, 1.0), 0.8, Metal(Color(0.7, 0.6, 0.5), 0.1)),
            Fog(Point3(0.0, 1.5, 0.0), 2.0, 0.35, Color(0.9, 0.9, 0.9)),
        ),
        SceneLook(Sky(Color(0.03, 0.03, 0.05), Color(0.0, 0.0, 0.02)), fov = 60.0, aperture = 0.0, sampleLights = true),
    )

    /** A prism of dispersive glass, a soap bubble and an iridescent ball on a dark floor under a lamp. */
    private val spectral = World(
        listOf(
            Sphere(Point3(0.0, -1000.0, 0.0), 1000.0, Lambert(Color(0.3, 0.3, 0.3))),
            Sphere(Point3(0.0, 1.0, 0.0), 1.0, Dielectric(1.6, dispersion = 0.08)),
            Sphere(Point3(-2.3, 1.0, 0.5), 1.0, SoapBubble(thickness = 420.0)),
            Sphere(Point3(2.3, 1.0, 0.5), 1.0, Iridescent(Color(0.03, 0.03, 0.03), 520.0, 1.8, 0.05)),
            Sphere(Point3(0.0, 6.0, 3.0), 1.0, Emissive(Color(1.0, 1.0, 1.0), 6.0)),
        ),
        SceneLook(Sky(Color(0.5, 0.6, 0.8), Color(0.1, 0.2, 0.5)), fov = 60.0, aperture = 0.0, sampleLights = true),
    )

    /** Rough gold, brushed aluminium, frosted glass and a smooth torus of copper. */
    private val microfacets = World(
        listOf(
            Sphere(Point3(0.0, -1000.0, 0.0), 1000.0, Checker(Color(0.8, 0.8, 0.8), Color(0.2, 0.2, 0.2), 1.0, 0.1)),
            Sphere(Point3(-2.4, 1.0, 0.0), 1.0, Conductor(Metals.GOLD, roughness = 0.35)),
            Sphere(Point3(2.4, 1.0, 0.0), 1.0, Conductor(Metals.ALUMINIUM, roughness = 0.4, anisotropy = 0.8)),
            Sphere(Point3(0.0, 1.0, 1.2), 0.9, Dielectric(1.5, roughness = 0.3)),
        ) + Meshes.smooth(
            Meshes.torus(Pose(Point3(0.0, 1.2, -1.5)), 1.2, 0.35, 32, 12, Conductor(Metals.COPPER, roughness = 0.2)),
        ),
    )

    /** Wax and jade balls and a velvet one, lit by a lamp with light sampling. */
    private val soft = World(
        listOf(
            Sphere(Point3(0.0, -1000.0, 0.0), 1000.0, Lambert(Color(0.6, 0.6, 0.6))),
            Translucent(Point3(-2.3, 1.0, 0.0), 1.0, Color(0.95, 0.8, 0.55), density = 4.0),
            Translucent(Point3(0.0, 1.0, 0.5), 1.0, Color(0.35, 0.85, 0.55), density = 3.0, roughness = 0.2),
            Sphere(Point3(2.3, 1.0, 0.0), 1.0, Velvet(Color(0.35, 0.02, 0.1), Color(0.9, 0.6, 0.7), 0.4)),
            Sphere(Point3(0.0, 5.0, 3.0), 0.8, Emissive(Color(1.0, 0.95, 0.9), 8.0)),
        ),
        SceneLook(Sky(Color(0.6, 0.65, 0.8), Color(0.2, 0.3, 0.6)), fov = 60.0, aperture = 0.0, sampleLights = true),
    )

    /** Bumped marble, wood, a cloudy ball and a noisy floor. */
    private val patterns = World(
        listOf(
            Sphere(Point3(0.0, -1000.0, 0.0), 1000.0, Patterned(Pattern(PatternKind.NOISE, GREY, WALL, 0.8), 0.3)),
            Sphere(
                Point3(-2.3, 1.0, 0.0), 1.0,
                Patterned(Pattern(PatternKind.MARBLE, Color(0.9, 0.9, 0.85), Color(0.2, 0.25, 0.3), 1.5), 0.05, 0.4),
            ),
            Sphere(
                Point3(0.0, 1.0, 0.5), 1.0,
                Patterned(Pattern(PatternKind.WOOD, Color(0.7, 0.45, 0.2), RED, 3.0), 0.2, 0.2),
            ),
            Sphere(Point3(2.3, 1.0, 0.0), 1.0, Patterned(Pattern(PatternKind.CLOUDS, WALL, Color.ONE, 2.0), 0.5)),
            Sphere(
                Point3(0.0, 0.4, 2.2), 0.4,
                Conductor(Metals.ALUMINIUM, 0.4, anisotropy = 0.9, brushing = Point3(1.0, 0.0, 0.0)),
            ),
        ) + Meshes.panel(
            Pose(Point3(-1.5, 2.6, -2.0)), 1.0, 0.5,
            GlowingPattern(
                Pattern(PatternKind.GRID, Color(0.1, 0.1, 0.1), Color(0.2, 0.9, 0.4), 4.0, frequency = 0.15), 2.0,
            ),
        ) + Meshes.panel(
            Pose(Point3(1.5, 2.6, -2.0)), 1.0, 0.5,
            GlowingPattern(
                Pattern(PatternKind.STRIPES, Color.ZERO, Color(0.9, 0.3, 0.2), 1.0, frequency = 3.0, octaves = 3), 2.0,
            ),
        ),
    )

    /** A lava ball and two lamps, warm and cold, over a grey floor at night. */
    private val glowing = World(
        listOf(
            Sphere(Point3(0.0, -1000.0, 0.0), 1000.0, Lambert(Color(0.6, 0.6, 0.6))),
            Sphere(
                Point3(0.0, 1.0, 0.0), 1.0,
                GlowingPattern(Pattern(PatternKind.NOISE, Color(1.0, 0.35, 0.0), Color(0.15, 0.0, 0.0), 2.5), 3.0),
            ),
            Sphere(Point3(-2.5, 0.7, 1.0), 0.4, Emissive.kelvin(1900.0, 6.0)),
            Sphere(Point3(2.5, 0.7, 1.0), 0.4, Emissive.kelvin(10000.0, 6.0)),
            Sphere(Point3(0.0, 0.5, 2.0), 0.5, Lambert(Color(0.8, 0.8, 0.8))),
        ),
        SceneLook(Sky(Color(0.02, 0.02, 0.03), Color(0.0, 0.0, 0.01)), fov = 60.0, aperture = 0.0, sampleLights = true),
    )

    /** Balls flying past over the shutter, one of them a moving lamp. */
    private val moving = World(
        listOf(
            Sphere(Point3(0.0, -1000.0, 0.0), 1000.0, Lambert(Color(0.6, 0.6, 0.6))),
            Sphere(Point3(-3.0, 1.0, 0.0), 0.8, Lambert(RED), velocity = Point3(2.5, 0.0, 0.0)),
            Sphere(Point3(1.0, 0.6, 1.5), 0.5, Emissive(Color(1.0, 0.8, 0.4), 6.0), velocity = Point3(0.0, 1.5, 0.0)),
            Sphere(Point3(2.5, 1.0, -1.0), 1.0, Metal(Color(0.7, 0.6, 0.5), 0.1)),
        ),
        SceneLook(Sky.DAYLIGHT, fov = 60.0, aperture = 0.0, sampleLights = true, motionBlur = true),
    )

    /**
     * A block of wax and a block of red resin bounding media of coloured density, in a bank of bluish fog that the
     * camera stands in, lit by a sampled lamp.
     */
    private val media: World
        get() {
            val haze = Medium(Color(0.05, 0.08, 0.12), Color(0.8, 0.85, 0.95))
            val wax = MediumInterface(Medium.ofMeanFreePath(Color(0.7, 0.35, 0.22), Color(0.95, 0.85, 0.7)), haze)
            val resin = MediumInterface(Medium(Color(0.9, 6.0, 9.0), Color(0.95, 0.4, 0.3)), haze)
            return World(
                listOf(
                    Sphere(Point3(0.0, -1000.0, 0.0), 1000.0, Lambert(Color(0.6, 0.6, 0.6))),
                    Fog(Point3(0.0, 1.0, 3.0), 9.0, haze),
                    Sphere(Point3(0.0, 4.0, 1.0), 0.6, Emissive(Color(1.0, 0.9, 0.8), 10.0)),
                ) + Meshes.box(Pose(Point3(-1.6, 0.8, 0.0)), Point3(0.8, 0.8, 0.8), Dielectric(1.4), wax) +
                    Meshes.box(
                        Pose(Point3(1.6, 0.8, 0.0)), Point3(0.7, 0.8, 0.7), Dielectric(1.5, roughness = 0.1), resin,
                    ),
                SceneLook(Sky(Color(0.1, 0.12, 0.16), Color(0.02, 0.03, 0.06)), 60.0, 0.0, sampleLights = true),
            )
        }

    /** A row of six letters of four pixels each, of growing brightness, and a big dim panel, over a floor. */
    private val sign = World(
        listOf(Sphere(Point3(0.0, -1000.0, 0.0), 1000.0, Lambert(Color(0.6, 0.6, 0.6)))) +
            (0 until 6).flatMap { letter ->
                (0 until 4).flatMap { pixel ->
                    Meshes.panel(
                        Pose(Point3(-2.5 + letter + (pixel % 2) * 0.3, 2.0 + (pixel / 2) * 0.3, -1.0)), 0.12, 0.12,
                        Emissive(Color(1.0, 0.2, 0.1), 4.0 + 6.0 * letter),
                    ).map { Triangle(it.v0, it.v1, it.v2, it.material, "letter_$letter") }
                }
            } +
            Meshes.panel(Pose(Point3(0.0, 5.0, -3.0)), 4.0, 1.0, Emissive(Color(0.4, 0.5, 0.9), 0.3)),
        SceneLook(Sky(Color(0.02, 0.02, 0.03), Color(0.0, 0.0, 0.01)), 60.0, 0.0, sampleLights = true),
    )

    /** The four fractals in a row: a gold bulb, a sponge, a box and a Julia set of glass. */
    private val fractals = World(
        listOf(
            Sphere(Point3(0.0, -1000.0, 0.0), 1000.0, Lambert(Color(0.6, 0.6, 0.6))),
            Fractal(FractalKind.MANDELBULB, Point3(-3.0, 1.0, 0.0), 1.0, Conductor(Metals.GOLD, 0.2)),
            Fractal(FractalKind.MENGER, Point3(-1.0, 1.0, 0.0), 0.9, Lambert(Color(0.8, 0.3, 0.2))),
            Fractal(FractalKind.MANDELBOX, Point3(1.0, 1.0, 0.0), 0.9, Plastic(Color(0.2, 0.5, 0.9), 0.1)),
            Fractal(FractalKind.JULIA, Point3(3.0, 1.0, 0.0), 1.0, Dielectric(1.5)),
        ),
    )

    private fun camera() = Camera(
        origin = Point3(0.0, 2.0, 7.0),
        target = Point3.ZERO,
        vup = Point3(0.0, 1.0, 0.0),
        fov = 60.0,
        aperture = 0.0,
        focusDist = 7.0,
        imageWidth = SIZE,
        imageHeight = SIZE,
    ).apply { samplesPerPixel = SAMPLES }

    @Test
    fun `GPU frame matches the CPU frame of the same scene in brightness`() {
        val camera = camera()
        val gpu = gpuFrame(camera)
        val cpu = cpuFrame(camera)

        val difference = abs(brightness(gpu) - brightness(cpu))
        assertTrue(difference < TOLERANCE, "GPU ${brightness(gpu)} vs CPU ${brightness(cpu)}")
        val regions = (0 until QUADRANTS).map { q -> abs(brightness(gpu, q) - brightness(cpu, q)) }
        assertTrue(regions.all { it < REGION_TOLERANCE }, "per quadrant $regions")
    }

    @Test
    fun `a GPU frame made in steps matches the CPU frame in brightness`() {
        val camera = camera()
        val gpu = progressiveGpuFrame(camera)
        val cpu = cpuFrame(camera)

        val difference = abs(brightness(gpu) - brightness(cpu))
        assertTrue(difference < TOLERANCE, "GPU in steps ${brightness(gpu)} vs CPU ${brightness(cpu)}")
    }

    @Test
    fun `with light sampling and fog the GPU frame matches the CPU frame in brightness`() {
        val camera = camera()
        val gpu = gpuFrame(camera, litFog)
        val cpu = cpuFrame(camera, litFog)

        val difference = abs(brightness(gpu) - brightness(cpu))
        assertTrue(difference < TOLERANCE, "GPU ${brightness(gpu)} vs CPU ${brightness(cpu)}")
        val regions = (0 until QUADRANTS).map { q -> abs(brightness(gpu, q) - brightness(cpu, q)) }
        assertTrue(regions.all { it < REGION_TOLERANCE }, "per quadrant $regions")
    }

    @Test
    fun `dispersive glass and thin films look the same on the GPU as on the CPU`() {
        val camera = camera()
        val gpu = gpuFrame(camera, spectral)
        val cpu = cpuFrame(camera, spectral)

        (0 until CHANNELS).forEach { channel ->
            val (onGpu, onCpu) = channelMean(gpu, channel) to channelMean(cpu, channel)
            assertTrue(abs(onGpu - onCpu) < TOLERANCE, "channel $channel: GPU $onGpu vs CPU $onCpu")
        }
        val regions = (0 until QUADRANTS).map { q -> abs(brightness(gpu, q) - brightness(cpu, q)) }
        assertTrue(regions.all { it < REGION_TOLERANCE }, "per quadrant $regions")
    }

    @Test
    fun `microfacet metals, frosted glass and smooth meshes look the same on the GPU as on the CPU`() {
        val camera = camera()
        val gpu = gpuFrame(camera, microfacets)
        val cpu = cpuFrame(camera, microfacets)

        (0 until CHANNELS).forEach { channel ->
            val (onGpu, onCpu) = channelMean(gpu, channel) to channelMean(cpu, channel)
            assertTrue(abs(onGpu - onCpu) < TOLERANCE, "channel $channel: GPU $onGpu vs CPU $onCpu")
        }
        val regions = (0 until QUADRANTS).map { q -> abs(brightness(gpu, q) - brightness(cpu, q)) }
        assertTrue(regions.all { it < REGION_TOLERANCE }, "per quadrant $regions")
    }

    @Test
    fun `translucent balls and velvet look the same on the GPU as on the CPU`() {
        val camera = camera()
        val gpu = gpuFrame(camera, soft)
        val cpu = cpuFrame(camera, soft)

        (0 until CHANNELS).forEach { channel ->
            val (onGpu, onCpu) = channelMean(gpu, channel) to channelMean(cpu, channel)
            assertTrue(abs(onGpu - onCpu) < TOLERANCE, "channel $channel: GPU $onGpu vs CPU $onCpu")
        }
        val regions = (0 until QUADRANTS).map { q -> abs(brightness(gpu, q) - brightness(cpu, q)) }
        assertTrue(regions.all { it < REGION_TOLERANCE }, "per quadrant $regions")
    }

    @Test
    fun `procedural patterns and bumps look the same on the GPU as on the CPU`() {
        val camera = camera()
        val gpu = gpuFrame(camera, patterns)
        val cpu = cpuFrame(camera, patterns)

        (0 until CHANNELS).forEach { channel ->
            val (onGpu, onCpu) = channelMean(gpu, channel) to channelMean(cpu, channel)
            assertTrue(abs(onGpu - onCpu) < TOLERANCE, "channel $channel: GPU $onGpu vs CPU $onCpu")
        }
        val regions = (0 until QUADRANTS).map { q -> abs(brightness(gpu, q) - brightness(cpu, q)) }
        assertTrue(regions.all { it < REGION_TOLERANCE }, "per quadrant $regions")
    }

    @Test
    fun `a glowing pattern is the same pattern on the GPU as on the CPU, pixel by pixel`() {
        val pattern = Pattern(PatternKind.MARBLE, Color(0.9, 0.5, 0.1), Color(0.1, 0.0, 0.3), 0.7)
        val wall = World(
            Meshes.panel(Pose(Point3(0.0, 1.0, -2.0)), 20.0, 20.0, GlowingPattern(pattern, 1.0)),
        )
        val camera = camera()
        val gpu = gpuFrame(camera, wall)
        val cpu = cpuFrame(camera, wall)
        val difference = gpu.indices.sumOf { i ->
            (0 until CHANNELS).sumOf { c -> channelDifference(gpu[i], cpu[i], c) }
        }
        assertTrue(difference / (gpu.size * CHANNELS) < PIXEL_TOLERANCE, "mean difference per channel $difference")
    }

    @Test
    fun `lights by temperature and glowing patterns look the same on the GPU as on the CPU`() {
        val camera = camera()
        val gpu = gpuFrame(camera, glowing)
        val cpu = cpuFrame(camera, glowing)

        (0 until CHANNELS).forEach { channel ->
            val (onGpu, onCpu) = channelMean(gpu, channel) to channelMean(cpu, channel)
            assertTrue(abs(onGpu - onCpu) < TOLERANCE, "channel $channel: GPU $onGpu vs CPU $onCpu")
        }
    }

    @Test
    fun `motion blur looks the same on the GPU as on the CPU`() {
        val camera = camera()
        val gpu = gpuFrame(camera, moving)
        val cpu = cpuFrame(camera, moving)

        (0 until CHANNELS).forEach { channel ->
            val (onGpu, onCpu) = channelMean(gpu, channel) to channelMean(cpu, channel)
            assertTrue(abs(onGpu - onCpu) < TOLERANCE, "channel $channel: GPU $onGpu vs CPU $onCpu")
        }
        val regions = (0 until QUADRANTS).map { q -> abs(brightness(gpu, q) - brightness(cpu, q)) }
        assertTrue(regions.all { it < REGION_TOLERANCE }, "per quadrant $regions")
    }

    @Test
    fun `a GPU frame made in bands of rows matches the one made whole`() {
        val camera = camera()
        val whole = progressiveGpuFrame(camera)
        val banded = GpuTracer().use { tracer ->
            val scene = GpuScenePacking.pack(world.objects)
            var frame: org.jetbrains.skia.Shader? = null
            var taken = 0
            GpuSampling.steps(SAMPLES).forEachIndexed { index, samples ->
                (0 until 3).forEach { band ->
                    val rows = GpuSampling.bandRows(SIZE, band, 3)
                    val seed = 0.1f * (index + 1) + band
                    val step = GpuFrame(scene, camera.view(), SIZE, SIZE, DEPTH, samples, seed, GpuBand(taken, rows))
                    frame = tracer.shader(step, frame)
                }
                taken += samples
            }
            pixels(requireNotNull(frame))
        }
        (0 until QUADRANTS).forEach { q ->
            assertTrue(abs(brightness(whole, q) - brightness(banded, q)) < REGION_TOLERANCE, "quadrant $q")
        }
    }

    @Test
    fun `a GPU frame made in the steps a slow scene gets is as bright and as sharp as one made whole`() {
        val camera = camera().apply { samplesPerPixel = BUDGET_SAMPLES }
        val budget = GpuSampling.StepBudget().after(SLOW_STEP_MS)
        val stepped = budgetedGpuFrame(camera, glowing, budget)
        val whole = budgetedGpuFrame(camera, glowing, GpuSampling.StepBudget(BUDGET_SAMPLES, 1))
        val cpu = cpuFrame(camera, glowing)

        val (inSteps, inOne) = brightness(stepped) to brightness(whole)
        assertTrue(abs(inSteps - inOne) < inOne * BRIGHTNESS_SHARE, "budget $budget: in steps $inSteps, whole $inOne")
        assertTrue(abs(inSteps - brightness(cpu)) < brightness(cpu) * CPU_BRIGHTNESS_SHARE, "CPU ${brightness(cpu)}")
        val sharpness = detail(stepped) / detail(whole)
        assertTrue(sharpness in SHARPNESS_RANGE, "the frame in steps has ${sharpness}x the detail of the whole one")
    }

    @Test
    fun `media in closed meshes and coloured fog look the same on the GPU as on the CPU`() {
        val camera = camera()
        val gpu = gpuFrame(camera, media)
        val cpu = cpuFrame(camera, media)

        (0 until CHANNELS).forEach { channel ->
            val (onGpu, onCpu) = channelMean(gpu, channel) to channelMean(cpu, channel)
            assertTrue(abs(onGpu - onCpu) < TOLERANCE, "channel $channel: GPU $onGpu vs CPU $onCpu")
        }
        val regions = (0 until QUADRANTS).map { q -> abs(brightness(gpu, q) - brightness(cpu, q)) }
        assertTrue(regions.all { it < REGION_TOLERANCE }, "per quadrant $regions")
    }

    @Test
    fun `a sign of many small lights of uneven brightness looks the same on the GPU as on the CPU`() {
        val camera = camera()
        val gpu = gpuFrame(camera, sign)
        val cpu = cpuFrame(camera, sign)

        (0 until CHANNELS).forEach { channel ->
            val (onGpu, onCpu) = channelMean(gpu, channel) to channelMean(cpu, channel)
            assertTrue(abs(onGpu - onCpu) < TOLERANCE, "channel $channel: GPU $onGpu vs CPU $onCpu")
        }
        val regions = (0 until QUADRANTS).map { q -> abs(brightness(gpu, q) - brightness(cpu, q)) }
        assertTrue(regions.all { it < REGION_TOLERANCE }, "per quadrant $regions")
    }

    @Test
    fun `fractals look the same on the GPU as on the CPU`() {
        val camera = camera()
        val gpu = gpuFrame(camera, fractals)
        val cpu = cpuFrame(camera, fractals)

        (0 until CHANNELS).forEach { channel ->
            val (onGpu, onCpu) = channelMean(gpu, channel) to channelMean(cpu, channel)
            assertTrue(abs(onGpu - onCpu) < TOLERANCE, "channel $channel: GPU $onGpu vs CPU $onCpu")
        }
        val regions = (0 until QUADRANTS).map { q -> abs(brightness(gpu, q) - brightness(cpu, q)) }
        assertTrue(regions.all { it < REGION_TOLERANCE }, "per quadrant $regions")
    }

    @Test
    fun `triangles are drawn`() {
        val wall = Triangle(
            Point3(-50.0, -50.0, -3.0),
            Point3(50.0, -50.0, -3.0),
            Point3(0.0, 50.0, -3.0),
            Lambert(RED),
        )
        val centre = gpuFrame(camera(), World(listOf(wall)))[SIZE / 2 * SIZE + SIZE / 2]
        val red = centre shr 16 and 255
        assertTrue(red > (centre and 255), "centre ${Integer.toHexString(centre)} is not the red wall")
    }

    private fun gpuFrame(camera: Camera, scene: World = world): IntArray = GpuTracer().use { tracer ->
        val frame = GpuFrame(scene.gpuScene, camera.view(), SIZE, SIZE, DEPTH, SAMPLES, 0.37f)
        pixels(tracer.shader(frame))
    }

    /** The frame as RenderController makes it under [budget]: steps of its samples, each over its bands of rows. */
    private fun budgetedGpuFrame(camera: Camera, scene: World, budget: GpuSampling.StepBudget): IntArray =
        GpuTracer().use { tracer ->
            var frame: org.jetbrains.skia.Shader? = null
            var taken = 0
            var seed = 0.1f
            while (taken < camera.samplesPerPixel) {
                val samples = GpuSampling.nextStep(taken, camera.samplesPerPixel, budget.samples)
                (0 until budget.bands).forEach { band ->
                    val rows = GpuSampling.bandRows(SIZE, band, budget.bands)
                    seed += 0.37f
                    val band = GpuBand(taken, rows)
                    val step = GpuFrame(scene.gpuScene, camera.view(), SIZE, SIZE, DEPTH, samples, seed, band)
                    frame = tracer.shader(step, frame)
                }
                taken += samples
            }
            pixels(requireNotNull(frame))
        }

    /** Mean difference between neighbouring pixels: how much detail, noise included, a frame keeps. */
    private fun detail(pixels: IntArray): Double = (0 until SIZE).sumOf { y ->
        (1 until SIZE).sumOf { x ->
            val (a, b) = pixels[y * SIZE + x] to pixels[y * SIZE + x - 1]
            (0 until CHANNELS).sumOf { channelDifference(a, b, it) }
        }
    } / (SIZE * (SIZE - 1) * CHANNELS)

    /** The frame as RenderController makes it in GPU mode: steps, each blended into the frame of those before. */
    private fun progressiveGpuFrame(camera: Camera): IntArray = GpuTracer().use { tracer ->
        val scene = GpuScenePacking.pack(world.objects)
        var frame: org.jetbrains.skia.Shader? = null
        var taken = 0
        GpuSampling.steps(SAMPLES).forEachIndexed { index, samples ->
            val step = GpuFrame(scene, camera.view(), SIZE, SIZE, DEPTH, samples, 0.1f * (index + 1), GpuBand(taken))
            frame = tracer.shader(step, frame)
            taken += samples
        }
        pixels(requireNotNull(frame))
    }

    private fun pixels(shader: org.jetbrains.skia.Shader): IntArray {
        val surface = Surface.makeRaster(ImageInfo.makeN32Premul(SIZE, SIZE))
        surface.canvas.drawPaint(Paint().apply { this.shader = shader })
        val bitmap = Bitmap.makeFromImage(surface.makeImageSnapshot())
        return IntArray(SIZE * SIZE) { bitmap.getColor(it % SIZE, it / SIZE) }
    }

    private fun cpuFrame(camera: Camera, scene: World = world): IntArray {
        val rayTracer = RayTracer(scene, DEPTH)
        val frameSeed = Random(5).nextLong()
        return IntArray(SIZE * SIZE) { index ->
            val pixel = FrameSampling.Pixel(index % SIZE, SIZE - 1 - index / SIZE)
            val random = FrameSampling.pixelRandom(frameSeed, pixel, SIZE)
            FrameSampling.samplePixel(pixel, camera, rayTracer, random).toAWTColor(camera.samplesPerPixel)
        }
    }

    private fun brightness(pixels: IntArray, quadrant: Int = -1): Double {
        val selected = pixels.indices.filter { quadrant < 0 || quadrantOf(it) == quadrant }
        return selected.sumOf { i ->
            val p = pixels[i]
            ((p shr 16 and 255) + (p shr 8 and 255) + (p and 255)) / 765.0
        } / selected.size
    }

    private fun channelMean(pixels: IntArray, channel: Int): Double =
        pixels.sumOf { (it shr (16 - 8 * channel) and 255) / 255.0 } / pixels.size

    private fun channelDifference(a: Int, b: Int, channel: Int): Double {
        val shift = 16 - 8 * channel
        return abs((a shr shift and 255) - (b shr shift and 255)) / 255.0
    }

    private fun quadrantOf(index: Int): Int =
        (if (index % SIZE < SIZE / 2) 0 else 1) + (if (index / SIZE < SIZE / 2) 0 else 2)

    private companion object {
        val WALL = Color(0.2, 0.4, 0.8)
        val RED = Color(0.8, 0.2, 0.1)
        val GREY = Color(0.5, 0.5, 0.5)
        const val SIZE = 48
        const val SAMPLES = 16
        const val DEPTH = 7
        const val TOLERANCE = 0.03
        const val REGION_TOLERANCE = 0.06
        const val QUADRANTS = 4
        const val CHANNELS = 3
        const val PIXEL_TOLERANCE = 0.02
        const val BUDGET_SAMPLES = 32
        const val SLOW_STEP_MS = 400.0
        const val BRIGHTNESS_SHARE = 0.03
        /** The first GPU steps take 1, 1 and 2 samples and clip bright lamps in the 8-bit frame; the CPU does not. */
        const val CPU_BRIGHTNESS_SHARE = 0.1
        val SHARPNESS_RANGE = 0.8..1.25
    }
}
