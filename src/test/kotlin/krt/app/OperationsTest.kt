package krt.app

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import krt.domain.camera.CameraMove
import krt.domain.camera.model.Flights
import krt.domain.camera.model.ViewPreset
import krt.domain.render.model.BenchmarkConfig
import krt.domain.scene.model.DemoScene
import krt.domain.render.model.RenderMode
import krt.infra.AppContext
import krt.platform.math.Point3

/**
 * Operations are tested through their public method with real resources (ADR 0007).
 * Wireframe mode renders synchronously, so every published frame is observable right after the call.
 */
class OperationsTest {

    private val app = AppContext.create(DemoScene.CLASSIC)
    private var published = 0

    init {
        app.frameBuffer.onUpdate { published++ }
        app.renderController.mode = RenderMode.WIREFRAME
    }

    @AfterTest
    fun close() = app.close()

    @Test
    fun `set render mode switches the renderer and redraws`() {
        app.setRenderMode(RenderMode.WIREFRAME)
        assertEquals(RenderMode.WIREFRAME, app.renderController.mode)
        assertEquals(1, published)
        assertTrue(app.frameBuffer.progress.isDone)
    }

    @Test
    fun `GPU mode hands the frame over as shaders, step by step`() {
        val done = CountDownLatch(1)
        app.frameBuffer.onGpuUpdate { if (app.frameBuffer.progress.isDone) done.countDown() }
        app.changeRenderSettings { repaintIntervalMs = 0 }
        app.setRenderMode(RenderMode.GPU)
        assertTrue(done.await(30, TimeUnit.SECONDS), "GPU frame did not finish")
        assertEquals(app.camera.samplesPerPixel, app.frameBuffer.progress.completedPasses)
        assertEquals(1, published)
    }

    @Test
    fun `camera settings resize the frame and redraw`() {
        app.changeCameraSettings { imageWidth = 120; imageHeight = 80 }
        assertEquals(120 to 80, app.frameBuffer.frame.width to app.frameBuffer.frame.height)
        assertEquals(1, published)
    }

    @Test
    fun `camera settings at the smallest accepted size resize and redraw`() {
        app.changeCameraSettings { imageWidth = 40 }
        assertEquals(40, app.frameBuffer.frame.width)
        assertEquals(1, published)
    }

    @Test
    fun `camera settings below the accepted size change the camera but neither resize nor redraw`() {
        app.changeCameraSettings { imageWidth = 20 }
        assertEquals(20, app.camera.imageWidth)
        assertEquals(400, app.frameBuffer.frame.width)
        assertEquals(0, published)
    }

    @Test
    fun `camera moves and rotations redraw, unknown keys too`() {
        val before = app.camera.project(Point3(-1.0, 0.0, 0.0))
        app.moveCamera(CameraMove.FORWARD)
        app.rotateCamera(10, 5)
        app.moveCamera(CameraMove.NONE)
        assertNotEquals(before, app.camera.project(Point3(-1.0, 0.0, 0.0)))
        assertEquals(3, published)
    }

    @Test
    fun `move object translates it by id and redraws`() {
        val before = app.world.objects.single { it.id == "TEST" }.linearize(1.0).first()
        app.moveObject("TEST", Point3(0.0, 1.0, 0.0))
        val after = app.world.objects.single { it.id == "TEST" }.linearize(1.0).first()
        assertEquals(before.start + Point3(0.0, 1.0, 0.0), after.start)
        assertEquals(1, published)
    }

    @Test
    fun `open scene replaces the scene with a pbrt file and stands the camera at its view`() {
        val file = File.createTempFile("krt", ".pbrt").apply {
            deleteOnExit()
            writeText("LookAt 0 1 9  0 1 0  0 1 0\nWorldBegin\n#krt Name \"Ball\"\nShape \"sphere\"\nShape \"curve\"\n")
        }
        val warnings = app.openScene(file)
        assertEquals(listOf("Ball"), app.world.objects.map { it.id })
        assertEquals(1, warnings.size)
        assertEquals(Point3(0.0, 1.0, 9.0), app.camera.origin)
        assertEquals(null, app.world.source)
        assertEquals(1, published)
    }

    @Test
    fun `settings changes redraw`() {
        app.changeRayTracerSettings { maxDepth = 3 }
        app.changeRenderSettings { batchSize = 10 }
        assertEquals(3, app.rayTracer.maxDepth)
        assertEquals(10, app.renderController.batchSize)
        assertEquals(2, published)
    }

    @Test
    fun `save image writes the current frame as PNG`() {
        app.redraw()
        val file = File.createTempFile("krt-frame", ".png").apply { deleteOnExit() }
        app.saveImage(file)
        val saved = ImageIO.read(file)
        val frame = app.frameBuffer.frame
        assertEquals(frame.width to frame.height, saved.width to saved.height)
        val savedPixels = saved.getRGB(0, 0, saved.width, saved.height, null, 0, saved.width)
        assertEquals(frame.pixels.map { it and RGB }, savedPixels.map { it and RGB })
    }

    @Test
    fun `save image in GPU mode writes the same view traced on the CPU`() {
        app.changeCameraSettings { imageWidth = 48; imageHeight = 32; samplesPerPixel = 2 }
        app.setRenderMode(RenderMode.GPU)
        val file = File.createTempFile("krt-gpu-frame", ".png").apply { delete(); deleteOnExit() }
        app.saveImage(file)
        val deadline = System.currentTimeMillis() + SAVE_TIMEOUT_MS
        while (!file.exists() && System.currentTimeMillis() < deadline) SwingUtilities.invokeAndWait {}
        SwingUtilities.invokeAndWait {} // the UI thread writes the file: let it finish
        val saved = ImageIO.read(file)
        assertEquals(48 to 32, saved.width to saved.height)
        assertTrue(saved.getRGB(0, 0, 48, 32, null, 0, 48).any { it and RGB != 0 })
    }

    @Test
    fun `camera view presets, reset and focus on an object change the camera and redraw`() {
        assertEquals(ViewPreset.START, app.camera.snapshot().preset)
        app.setCameraView(ViewPreset.FRONT)
        assertEquals(ViewPreset.FRONT, app.camera.snapshot().preset)
        assertEquals(ViewPreset.FRONT.focusDistance, app.camera.focusDist)

        app.changeCameraSettings { fov = 30.0; aperture = 2.0 }
        app.resetCameraView()
        val reset = app.camera.snapshot()
        assertEquals(ViewPreset.START, reset.preset)
        val lens = Triple(reset.fov, reset.aperture, reset.focusDistance)
        assertEquals(Triple(90.0, 0.1, ViewPreset.START.focusDistance), lens)

        app.focusOnObject("TEST")
        assertEquals((Point3(-4.0, 1.0, 0.0) - app.camera.origin).length(), app.camera.focusDist, 1e-9)
        app.focusOnObject("no such object")
        assertEquals(4, published)
    }

    @Test
    fun `load scene replaces the scene, its look and the camera lens, and redraws`() {
        app.loadScene(DemoScene.SHOWCASE)
        val look = app.scenes.load(DemoScene.SHOWCASE).look
        assertEquals(look, app.world.look)
        assertEquals(DemoScene.SHOWCASE, app.world.source)
        assertTrue(app.world.objects.any { it.id == "Mirror ball" })
        assertEquals(ViewPreset.START, app.camera.snapshot().preset)
        assertEquals(look.fov, app.camera.fov)
        app.changeCameraSettings { fov = 70.0 }
        app.resetCameraView()
        assertEquals(look.fov, app.camera.fov)
        assertEquals(3, published)
    }

    @Test
    fun `a benchmark run sets up its scene and renderer, flies the camera and the settings come back`() {
        app.changeCameraSettings { imageWidth = 120; imageHeight = 80; samplesPerPixel = 3 }
        val before = app.captureSettings()
        app.startBenchmarkRun(BenchmarkConfig(DemoScene.SHOWCASE, RenderMode.WIREFRAME, 64, 48))
        assertEquals(64 to 48, app.frameBuffer.frame.width to app.frameBuffer.frame.height)
        assertEquals(BenchmarkConfig.SAMPLES, app.camera.samplesPerPixel)
        val pose = Flights.of(DemoScene.SHOWCASE).pose(100, 600)
        app.flyCamera(pose)
        assertEquals(pose.origin, app.camera.origin)
        app.restoreSettings(before)
        assertEquals(before, app.captureSettings())
        assertEquals(4, published)
    }

    private companion object {
        const val SAVE_TIMEOUT_MS = 30_000L
        const val RGB = 0xFFFFFF
    }
}
