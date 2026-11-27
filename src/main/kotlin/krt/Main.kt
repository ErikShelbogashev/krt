package krt

import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import krt.app.camera.CameraSettings
import krt.app.render.RayTracerSettings
import krt.app.render.RenderControllerSettings
import krt.app.scene.LoadObject
import krt.app.scene.ObjectController
import krt.domain.camera.CameraMove
import krt.infra.AppContext

fun main() {
    val app = AppContext.create()
    application {
        var imageBitmap by remember { mutableStateOf(app.frameBuffer.image.toComposeImageBitmap()) }
        LaunchedEffect(Unit) {
            app.frameBuffer.onUpdate { imageBitmap = it.toComposeImageBitmap() }
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
                    Image(bitmap = imageBitmap, contentDescription = null)
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

            Row {
                LoadObject(onFilePicked = { app.loadModel(it) })
                Button(onClick = { app.toggleWireframe() }) {
                    Text("Toggle Wireframe mode")
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

private fun cameraMoveFor(key: Key): CameraMove = when (key) {
    Key.W, Key.DirectionUp -> CameraMove.FORWARD
    Key.S, Key.DirectionDown -> CameraMove.BACKWARD
    Key.A, Key.DirectionLeft -> CameraMove.LEFT
    Key.D, Key.DirectionRight -> CameraMove.RIGHT
    Key.R -> CameraMove.UP
    Key.F -> CameraMove.DOWN
    else -> CameraMove.NONE
}
