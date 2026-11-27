package krt.app.render

import java.io.File
import krt.domain.camera.Camera
import krt.domain.camera.model.FlightPose
import krt.domain.render.FrameBuffer
import krt.domain.render.RayTracer
import krt.domain.render.RenderController
import krt.domain.render.model.Benchmark
import krt.domain.render.model.BenchmarkConfig
import krt.domain.render.model.BenchmarkResult
import krt.domain.render.model.FrameQuality
import krt.domain.render.model.SettingsSnapshot
import krt.domain.render.model.ViewState
import krt.domain.render.renderFrame
import krt.domain.scene.World
import krt.i9ns.scenefile.SceneLibrary
import krt.i9ns.textfile.TextFiles

class CaptureSettingsOp(
    private val world: World,
    private val camera: Camera,
    private val renderController: RenderController,
    private val rayTracer: RayTracer,
) {
    operator fun invoke(): SettingsSnapshot = SettingsSnapshot(
        objects = world.objects,
        look = world.look,
        mode = renderController.mode,
        quality = FrameQuality(camera.imageWidth, camera.imageHeight, camera.samplesPerPixel, rayTracer.maxDepth),
        view = ViewState(camera.origin, camera.lookAt, camera.fov, camera.aperture, camera.focusDist),
    )
}

class StartBenchmarkRunOp(
    private val world: World,
    private val scenes: SceneLibrary,
    private val camera: Camera,
    private val renderController: RenderController,
    private val rayTracer: RayTracer,
    private val frameBuffer: FrameBuffer,
) {
    /** Sets up one run: its scene, renderer, frame size, the benchmark's samples and depth, the start view. */
    operator fun invoke(config: BenchmarkConfig) {
        val scene = scenes.load(config.scene)
        world.load(scene.objects, scene.look, config.scene)
        renderController.mode = config.mode
        rayTracer.maxDepth = BenchmarkConfig.DEPTH
        camera.samplesPerPixel = BenchmarkConfig.SAMPLES
        camera.imageWidth = config.width
        camera.imageHeight = config.height
        camera.resetView(scene.look)
        frameBuffer.resizeTo(config.width, config.height)
        renderFrame(renderController, rayTracer, camera, frameBuffer)
    }
}

class FlyCameraOp(
    private val camera: Camera,
    private val renderController: RenderController,
    private val rayTracer: RayTracer,
    private val frameBuffer: FrameBuffer,
) {
    operator fun invoke(pose: FlightPose) {
        camera.lookFrom(pose.origin, pose.target)
        renderFrame(renderController, rayTracer, camera, frameBuffer)
    }
}

class RestoreSettingsOp(
    private val world: World,
    private val camera: Camera,
    private val renderController: RenderController,
    private val rayTracer: RayTracer,
    private val frameBuffer: FrameBuffer,
) {
    operator fun invoke(settings: SettingsSnapshot) {
        world.load(settings.objects, settings.look)
        renderController.mode = settings.mode
        rayTracer.maxDepth = settings.quality.depth
        camera.samplesPerPixel = settings.quality.samples
        camera.imageWidth = settings.quality.width
        camera.imageHeight = settings.quality.height
        camera.fov = settings.view.fov
        camera.aperture = settings.view.aperture
        camera.focusDist = settings.view.focusDistance
        camera.lookFrom(settings.view.origin, settings.view.lookAt)
        frameBuffer.resizeTo(settings.quality.width, settings.quality.height)
        renderFrame(renderController, rayTracer, camera, frameBuffer)
    }
}

class SaveBenchmarkReportOp(private val textFiles: TextFiles) {
    /** Writes [results] to [file] as CSV, one row per run. */
    operator fun invoke(file: File, results: List<BenchmarkResult>) {
        textFiles.write(file, Benchmark.csv(results))
    }
}
