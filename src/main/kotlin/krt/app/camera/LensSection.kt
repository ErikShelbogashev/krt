package krt.app.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import krt.app.shell.NumberField
import krt.app.shell.SectionHeader
import krt.domain.camera.Camera
import krt.domain.camera.model.CameraSnapshot
import krt.domain.camera.model.Lens

private val FOV_RANGE = 1.0..180.0

/** Lens diagram and the field of view: a number, a slider and ready lenses, kept in step. */
@Composable
fun LensSection(camera: CameraSnapshot, onChange: (Camera.() -> Unit) -> Unit, modifier: Modifier = Modifier) {
    var fov by remember(camera.fov) { mutableDoubleStateOf(camera.fov) }
    val setFov: (Double) -> Unit = {
        fov = it
        onChange { this.fov = it }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Lens")
        LensDiagram(fov, camera.focusDistance, camera.aperture)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Field of view", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(
                "≈ ${Lens.focalLengthMm(fov)} mm",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            NumberField(fov, onValid = setFov, range = FOV_RANGE, decimals = 0)
        }
        Slider(
            value = fov.toFloat(),
            onValueChange = { setFov(it.toDouble()) },
            valueRange = FOV_RANGE.start.toFloat()..FOV_RANGE.endInclusive.toFloat(),
            steps = 178,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Lens.FOV_PRESETS.forEach { (name, degrees) ->
                FilterChip(
                    selected = fov == degrees,
                    onClick = { setFov(degrees) },
                    label = { Text("$name ${degrees.toInt()}°") },
                )
            }
        }
    }
}
