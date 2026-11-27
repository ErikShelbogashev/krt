package krt.app

import java.io.File
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import krt.domain.camera.CameraMove
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
        app.renderController.toggleWireframeMode()
    }

    @AfterTest
    fun close() = app.renderController.close()

    @Test
    fun `toggle wireframe switches mode and redraws`() {
        app.toggleWireframe() // back to ray tracing: asynchronous, so switch again before checking
        app.toggleWireframe()
        assertEquals(1, published)
    }

    @Test
    fun `camera settings resize the frame and redraw`() {
        app.changeCameraSettings { imageWidth = 120; imageHeight = 80 }
        assertEquals(120 to 80, app.frameBuffer.image.width to app.frameBuffer.image.height)
        assertEquals(1, published)
    }

    @Test
    fun `camera settings at the smallest accepted size resize and redraw`() {
        app.changeCameraSettings { imageWidth = 40 }
        assertEquals(40, app.frameBuffer.image.width)
        assertEquals(1, published)
    }

    @Test
    fun `camera settings below the accepted size change the camera but neither resize nor redraw`() {
        app.changeCameraSettings { imageWidth = 20 }
        assertEquals(20, app.camera.imageWidth)
        assertEquals(400, app.frameBuffer.image.width)
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
        assertEquals(objectsBefore + 326, app.world.objects.size)
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
}
