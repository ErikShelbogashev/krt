package krt.app.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import krt.app.shell.MonoValue
import krt.app.shell.SectionHeader
import krt.domain.camera.model.CameraSnapshot
import krt.domain.camera.model.ViewPreset
import krt.platform.math.Point3
import krt.platform.text.NumberInput

/** Ready views, a reset, and where the camera stands and looks right now. */
@Composable
fun ViewSection(
    camera: CameraSnapshot,
    onPreset: (ViewPreset) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("View")
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onReset, contentPadding = SMALL_BUTTON, modifier = Modifier.height(30.dp)) {
                Text("Reset view", style = MaterialTheme.typography.labelMedium)
            }
        }
        val current = camera.preset
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ViewPreset.entries.forEach { preset ->
                PresetButton(preset.title, preset == current, onClick = { onPreset(preset) }, Modifier.weight(1f))
            }
        }
        PositionChip("Position", camera.origin)
        PositionChip("Look at", camera.lookAt)
    }
}

@Composable
private fun PresetButton(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label: @Composable () -> Unit = { Text(title, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
    if (selected) {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier,
            contentPadding = SMALL_BUTTON,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) { label() }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = SMALL_BUTTON) { label() }
    }
}

@Composable
private fun PositionChip(label: String, point: Point3) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        MonoValue(
            listOf(point.x, point.y, point.z).joinToString("  ") { NumberInput.format(it, 2) },
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val SMALL_BUTTON = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
