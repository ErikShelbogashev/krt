package krt.app.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import krt.domain.camera.Camera

@Composable
fun CameraSettings(
    camera: Camera,
    onChange: (Camera.() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Camera Settings", style = MaterialTheme.typography.h6)

        var fov by remember { mutableDoubleStateOf(camera.fov) }
        Text("Field of View: ${fov.toInt()}°")
        Slider(
            value = fov.toFloat(),
            onValueChange = { newValue ->
                fov = newValue.toDouble()
                onChange { this.fov = newValue.toDouble() }
            },
            valueRange = 1f..180f,
            steps = 179,
            modifier = Modifier.width(200.dp)
        )

        var aperture by remember { mutableDoubleStateOf(camera.aperture) }
        Text("Aperture: $aperture")
        Slider(
            value = aperture.toFloat(),
            onValueChange = { newValue ->
                aperture = newValue.toDouble()
                onChange { this.aperture = newValue.toDouble() }
            },
            valueRange = 0.0f..10f,
            steps = 199,
            modifier = Modifier.width(200.dp)
        )

        var focusDist by remember { mutableDoubleStateOf(camera.focusDist) }
        Text("Focus Distance: $focusDist")
        Slider(
            value = focusDist.toFloat(),
            onValueChange = { newValue ->
                focusDist = newValue.toDouble()
                onChange { this.focusDist = newValue.toDouble() }
            },
            valueRange = 0.0f..10.0f,
            steps = 999,
            modifier = Modifier.width(200.dp)
        )

        var samplesPerPixel by remember { mutableIntStateOf(camera.samplesPerPixel) }
        Text("Samples per pixel: $samplesPerPixel")
        Slider(
            value = samplesPerPixel.toFloat(),
            onValueChange = { newValue ->
                samplesPerPixel = newValue.toInt()
                onChange { this.samplesPerPixel = samplesPerPixel }
            },
            valueRange = 1.0f..50.0f,
            steps = 49,
            modifier = Modifier.width(200.dp)
        )

        var movementSpeed by remember { mutableDoubleStateOf(camera.movementSpeed) }
        Text("Movement speed: $movementSpeed")
        Slider(
            value = movementSpeed.toFloat(),
            onValueChange = { newValue ->
                movementSpeed = newValue.toDouble()
                onChange { this.movementSpeed = movementSpeed }
            },
            valueRange = 0.1f..3.0f,
            steps = 100,
            modifier = Modifier.width(200.dp)
        )

        var imageWidth by remember { mutableIntStateOf(camera.imageWidth) }
        OutlinedTextField(
            value = imageWidth.toString(),
            onValueChange = { newValue ->
                newValue.toIntOrNull()?.let {
                    imageWidth = it
                    if (it !in 32..1024) return@let
                    onChange { this.imageWidth = it }
                }
            },
            label = { Text("Image Width") }
        )

        var imageHeight by remember { mutableIntStateOf(camera.imageHeight) }
        OutlinedTextField(
            value = imageHeight.toString(),
            onValueChange = { newValue ->
                newValue.toIntOrNull()?.let {
                    imageHeight = it
                    if (it !in 32..1024) return@let
                    onChange { this.imageHeight = it }
                }
            },
            label = { Text("Image Height") }
        )
    }
}
