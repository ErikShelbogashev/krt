package krt.app.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale
import krt.app.shell.KeyHint
import krt.app.shell.SectionHeader
import krt.app.shell.SliderRow
import krt.domain.camera.Camera

/** Camera tab of the inspector: the lens and how the camera moves. */
@Composable
fun CameraTab(camera: Camera, onChange: (Camera.() -> Unit) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader("Lens")
        LensSection(camera, onChange)
        SectionHeader("Navigation")
        var speed by remember { mutableFloatStateOf(camera.movementSpeed.toFloat()) }
        SliderRow(
            label = "Movement speed",
            valueText = speed.format("%.1f"),
            value = speed,
            onValueChange = {
                speed = it
                onChange { movementSpeed = it.toDouble() }
            },
            range = 0.1f..3f,
            steps = 28,
        )
        KeyLegend()
    }
}

@Composable
private fun LensSection(camera: Camera, onChange: (Camera.() -> Unit) -> Unit) {
    var fov by remember { mutableFloatStateOf(camera.fov.toFloat()) }
    SliderRow(
        label = "Field of view",
        valueText = "${fov.toInt()}°",
        value = fov,
        onValueChange = {
            fov = it
            onChange { this.fov = it.toDouble() }
        },
        range = 1f..180f,
        steps = 178,
    )
    var aperture by remember { mutableFloatStateOf(camera.aperture.toFloat()) }
    SliderRow(
        label = "Aperture",
        valueText = aperture.format("%.2f"),
        value = aperture,
        onValueChange = {
            aperture = it
            onChange { this.aperture = it.toDouble() }
        },
        range = 0f..10f,
        steps = 199,
    )
    var focusDist by remember { mutableFloatStateOf(camera.focusDist.toFloat()) }
    SliderRow(
        label = "Focus distance",
        valueText = focusDist.format("%.2f"),
        value = focusDist,
        onValueChange = {
            focusDist = it
            onChange { this.focusDist = it.toDouble() }
        },
        range = 0f..10f,
        steps = 199,
    )
}

@Composable
private fun KeyLegend() {
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyHint("W", "forward")
            KeyHint("A", "left")
            KeyHint("R", "up")
            KeyHint("Drag", "rotate")
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyHint("S", "back")
            KeyHint("D", "right")
            KeyHint("F", "down")
            KeyHint("↑ ↓ ← →", "move")
        }
    }
}

private fun Float.format(pattern: String): String = String.format(Locale.ROOT, pattern, this)
