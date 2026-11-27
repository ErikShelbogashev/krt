package krt.app.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import krt.app.shell.MonoValue
import krt.app.shell.SectionHeader
import krt.domain.camera.Camera
import krt.platform.text.NumberInput

private val SPEEDS = listOf("Slow" to 0.05, "Normal" to 0.1, "Fast" to 0.5)
private val SPEED_RANGE = 0.05f..3f

/** How far a key press moves the camera, and which keys move it. */
@Composable
fun NavigationSection(movementSpeed: Double, onChange: (Camera.() -> Unit) -> Unit, modifier: Modifier = Modifier) {
    var speed by remember(movementSpeed) { mutableDoubleStateOf(movementSpeed) }
    val set: (Double) -> Unit = {
        speed = it
        onChange { this.movementSpeed = it }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Navigation")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Movement speed", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            MonoValue(NumberInput.format(speed, 2))
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SPEEDS.forEachIndexed { index, (name, value) ->
                SegmentedButton(
                    selected = speed == value,
                    onClick = { set(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, SPEEDS.size),
                    icon = {},
                ) { Text("$name ${NumberInput.format(value, 2)}", style = MaterialTheme.typography.labelMedium) }
            }
        }
        Slider(
            value = speed.toFloat().coerceIn(SPEED_RANGE),
            onValueChange = { set(it.toDouble()) },
            valueRange = SPEED_RANGE,
        )
        KeyLegend()
    }
}

@Composable
private fun KeyLegend() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            KeyCap("W")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                KeyCap("A")
                KeyCap("S")
                KeyCap("D")
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            KeyCap("R")
            KeyCap("F")
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LegendText("W A S D — move")
            LegendText("R / F — up / down")
            LegendText("Drag on frame — look around")
        }
    }
}

@Composable
private fun KeyCap(key: String) {
    Box(
        Modifier
            .size(26.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(key, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun LegendText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
