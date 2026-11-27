package krt.app

import java.io.File
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import krt.domain.camera.CameraMove
import krt.domain.render.model.RenderMode
import krt.infra.AppContext
import krt.platform.math.Point3

/**
 * Operations are tested through their public method with real resources (ADR 0007).
 * Wireframe mode renders synchronously, so every published frame is observable right after the call.
 */
class OperationsTest {

    private val app = AppContext.create(Random(1))
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
    fun `GPU mode hands the frame over as a shader`() {
        var gpuFrames = 0
        app.frameBuffer.onGpuUpdate { gpuFrames++ }
        app.setRenderMode(RenderMode.GPU)
        assertEquals(1, gpuFrames)
        assertEquals(0, published)
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
        val before = app.camera.project(Point3(5.0, 1.0, 1.0))
        app.moveCamera(CameraMove.FORWARD)
        app.rotateCamera(10, 5)
        app.moveCamera(CameraMove.NONE)
        assertNotEquals(before, app.camera.project(Point3(5.0, 1.0, 1.0)))
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
    fun `load model adds its triangles to the scene and redraws`() {
        val objectsBefore = app.world.objects.size
        app.loadModel(File("samples/ORIGAMI_Chat_Free.obj"))
        assertEquals(objectsBefore + 330, app.world.objects.size)
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

    private companion object {
        const val RGB = 0xFFFFFF
    }
}
