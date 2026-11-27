package krt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import krt.app.camera.CameraSettings
import krt.app.render.RayTracerSettings
import krt.app.render.RenderControllerSettings
import krt.app.scene.LoadObject
import krt.app.scene.ObjectController
import krt.domain.camera.CameraMove
import krt.domain.render.Frame
import krt.domain.render.model.RenderMode
import krt.infra.AppContext
import krt.platform.skia.ArgbBitmapConverter

fun main() {
    val app = AppContext.create()
    application {
        val converter = remember { ArgbBitmapConverter() }
        var imageBitmap by remember { mutableStateOf(converter.toImageBitmap(app.frameBuffer.frame)) }
        var gpuFrame by remember { mutableStateOf<Shader?>(null) }
        LaunchedEffect(Unit) {
            app.frameBuffer.onUpdate {
                imageBitmap = converter.toImageBitmap(it)
                gpuFrame = null
            }
            app.frameBuffer.onGpuUpdate { gpuFrame = it.asComposeShader() }
            app.redraw()
        }

        Window(
            onCloseRequest = {
                app.close()
                exitApplication()
            },
            title = "Ray Tracing"
        ) {
            val focusRequester = remember { FocusRequester() }
            val scrollState = rememberScrollState()
            var prevX by remember { mutableIntStateOf(0) }
            var prevY by remember { mutableIntStateOf(0) }

            Column(modifier = Modifier.focusRequester(focusRequester).pointerInput(Unit) {
                detectDragGestures(onDragStart = { offset ->
                    prevX = offset.x.toInt()
                    prevY = offset.y.toInt()
                }, onDrag = { change, _ ->
                    app.rotateCamera(change.position.x.toInt() - prevX, change.position.y.toInt() - prevY)
                    prevX = change.position.x.toInt()
                    prevY = change.position.y.toInt()
                    change.consume()
                })
            }.focusable().onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    app.moveCamera(cameraMoveFor(keyEvent.key))
                    true
                } else false
            }) {
                Row {
                    FrameView(imageBitmap, gpuFrame, app.camera.imageWidth, app.camera.imageHeight)
                    Box(Modifier.verticalScroll(scrollState)) {
                        SettingsPanel(app)
                    }
                }
            }
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }
    }
}

@Composable
private fun SettingsPanel(app: AppContext, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Start)
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))

        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Toggles", style = MaterialTheme.typography.titleLarge)

            LoadObject(onFilePicked = { app.loadModel(it) })
            var mode by remember { mutableStateOf(app.renderController.mode) }
            SingleChoiceSegmentedButtonRow {
                RenderMode.entries.forEach { entry ->
                    SegmentedButton(
                        selected = entry == mode,
                        onClick = {
                            mode = entry
                            app.setRenderMode(entry)
                        },
                        shape = SegmentedButtonDefaults.itemShape(entry.ordinal, RenderMode.entries.size),
                    ) { Text(entry.title) }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))

        RayTracerSettings(app.rayTracer, onChange = { app.changeRayTracerSettings(it) })

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))

        RenderControllerSettings(app.renderController, onChange = { app.changeRenderSettings(it) })

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))

        CameraSettings(app.camera, onChange = { app.changeCameraSettings(it) })

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))

        ObjectController(
            speed = app.camera.movementSpeed,
            loadObjectIds = { app.world.getObjectIds() },
            onMove = { id, offset -> app.moveObject(id, offset) },
        )
    }
}

/** The CPU frame as pixels, or the GPU frame drawn by its shader at the frame's size in pixels (ADR 0009). */
@Composable
private fun FrameView(
    imageBitmap: ImageBitmap,
    gpuFrame: Shader?,
    width: Int,
    height: Int,
    modifier: Modifier = Modifier,
) {
    if (gpuFrame == null) {
        Image(bitmap = imageBitmap, contentDescription = null, modifier = modifier)
        return
    }
    val size = with(LocalDensity.current) { DpSize(width.toDp(), height.toDp()) }
    Canvas(modifier.size(size)) { drawRect(ShaderBrush(gpuFrame)) }
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
