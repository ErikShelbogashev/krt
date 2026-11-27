import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import raytracer.infra.AppContext
import raytracer.model.CameraMove
import raytracer.view.*

fun main() {
    val app = AppContext.create()
    application {
        var imageBitmap by remember { mutableStateOf(app.frameBuffer.image.toComposeImageBitmap()) }
        remember {
            app.frameBuffer.onUpdate { imageBitmap = it.toComposeImageBitmap() }
            app.redraw()
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = "Ray Tracing"
        ) {
            val focusRequester = remember { FocusRequester() }
            val scrollState = rememberScrollState()
            var prevX by remember { mutableStateOf(0) }
            var prevY by remember { mutableStateOf(0) }

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
                        settingsPanel(app)
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
fun settingsPanel(app: AppContext) {
    Column(
        modifier = Modifier
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.h5,
            color = MaterialTheme.colors.onSurface,
            modifier = Modifier.align(Alignment.Start)
        )

        Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.2f))

        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Toggles", style = MaterialTheme.typography.h6)

            Row {
                LoadObject { app.loadModel(it) }
                Button(onClick = { app.toggleWireframe() }) {
                    Text("Toggle Wireframe mode")
                }
            }
        }

        Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.2f))

        RayTracerSettings(app.rayTracer) { app.changeRayTracerSettings(it) }

        Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.2f))

        RenderControllerSettings(app.renderController) { app.changeRenderSettings(it) }

        Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.2f))

        CameraSettings(app.camera) { app.changeCameraSettings(it) }

        Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.2f))

        ObjectController(app.camera.movementSpeed, { app.world.getObjectIds() }) { id, offset -> app.moveObject(id, offset) }
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
