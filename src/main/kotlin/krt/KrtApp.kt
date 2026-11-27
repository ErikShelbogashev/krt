package krt

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.IntSize
import krt.app.camera.CameraTab
import krt.app.render.RenderModeSelector
import krt.app.render.RenderTab
import krt.app.render.StatusBar
import krt.app.render.Viewport
import krt.app.scene.LoadModelButton
import krt.app.scene.SceneTab
import krt.app.shell.Inspector
import krt.app.shell.KrtTheme
import krt.app.shell.TopBar
import krt.domain.camera.CameraMove
import krt.domain.render.Frame
import krt.domain.render.model.RenderProgress
import krt.domain.scene.model.Traceable
import krt.infra.AppContext
import krt.platform.skia.ArgbBitmapConverter

/** What the window shows of the last published frame; listeners of the frame buffer write it. */
private class ShownFrame(objects: List<Traceable>) {
    var image by mutableStateOf<ImageBitmap?>(null)
    var gpuFrame by mutableStateOf<Shader?>(null)
    var size by mutableStateOf(IntSize.Zero)
    var progress by mutableStateOf(RenderProgress.NONE)
    var objects by mutableStateOf(objects, referentialEqualityPolicy())
}

/** The window's content: views of the features wired to the operations of [app]; the lambdas are the ports. */
@Composable
fun KrtApp(app: AppContext, modifier: Modifier = Modifier) {
    val shown = rememberShownFrame(app)
    var dark by remember { mutableStateOf(true) }
    var mode by remember { mutableStateOf(app.renderController.mode) }
    KrtTheme(dark) {
        Surface(modifier, color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopBar(
                    onRestart = { app.redraw() },
                    dark = dark,
                    onToggleTheme = { dark = !dark },
                    modeSelector = {
                        RenderModeSelector(mode, onSelect = {
                            mode = it
                            app.setRenderMode(it)
                        })
                    },
                    actions = { LoadModelButton(onOpen = { app.loadModel(it) }) },
                )
                Row(Modifier.weight(1f)) {
                    Viewport(
                        image = shown.image,
                        gpuFrame = shown.gpuFrame,
                        frameSize = shown.size,
                        modeTitle = mode.title,
                        onRotate = { dx, dy -> app.rotateCamera(dx, dy) },
                        onKey = { app.moveCamera(cameraMoveFor(it)) },
                        modifier = Modifier.weight(1f),
                    )
                    AppInspector(app, shown.objects)
                }
                StatusBar(mode, shown.progress, app.renderController.threads, app.rayTracer.maxDepth)
            }
        }
    }
}

@Composable
private fun AppInspector(app: AppContext, objects: List<Traceable>) {
    Inspector(
        renderTab = {
            RenderTab(
                app.camera,
                app.rayTracer,
                app.renderController,
                onCameraChange = { app.changeCameraSettings(it) },
                onRayTracerChange = { app.changeRayTracerSettings(it) },
                onRenderChange = { app.changeRenderSettings(it) },
            )
        },
        cameraTab = { CameraTab(app.camera, onChange = { app.changeCameraSettings(it) }) },
        sceneTab = {
            SceneTab(objects, app.camera.movementSpeed, onMove = { id, offset -> app.moveObject(id, offset) })
        },
    )
}

/** Subscribes to the frame buffer once and draws the first frame. */
@Composable
private fun rememberShownFrame(app: AppContext): ShownFrame {
    val shown = remember { ShownFrame(app.world.objects) }
    val converter = remember { ArgbBitmapConverter() }
    LaunchedEffect(Unit) {
        app.frameBuffer.onUpdate { frame ->
            shown.image = converter.toImageBitmap(frame)
            shown.gpuFrame = null
            shown.size = IntSize(frame.width, frame.height)
            shown.progress = app.frameBuffer.progress
            shown.objects = app.world.objects
        }
        app.frameBuffer.onGpuUpdate {
            shown.gpuFrame = it.asComposeShader()
            shown.size = IntSize(app.camera.imageWidth, app.camera.imageHeight)
            shown.progress = app.frameBuffer.progress
            shown.objects = app.world.objects
        }
        app.redraw()
    }
    return shown
}

private fun cameraMoveFor(key: Key): CameraMove = when (key) {
    Key.W, Key.DirectionUp -> CameraMove.FORWARD
    Key.S, Key.DirectionDown -> CameraMove.BACKWARD
    Key.A, Key.DirectionLeft -> CameraMove.LEFT
    Key.D, Key.DirectionRight -> CameraMove.RIGHT
    Key.R -> CameraMove.UP
    Key.F -> CameraMove.DOWN
    else -> CameraMove.NONE
}

private fun ArgbBitmapConverter.toImageBitmap(frame: Frame): ImageBitmap =
    toImageBitmap(frame.pixels, frame.width, frame.height)
