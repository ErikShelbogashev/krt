package krt.app.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import krt.app.shell.NumberField
import krt.app.shell.SectionHeader
import krt.domain.camera.Camera
import krt.domain.camera.model.CameraSnapshot
import krt.domain.camera.model.Lens
import krt.domain.scene.model.ObjectSummary

private val APERTURE_RANGE = 0.01..10.0
private val FOCUS_FIELD_RANGE = 0.1..1000.0
private val FOCUS_SLIDER_RANGE = 0.1f..10f
private const val FIRST_APERTURE = 0.1

/**
 * Depth of field on a switch: off is a pinhole (aperture 0), on brings back the last aperture.
 * With it on: the aperture, the focus distance and focusing on the [selected] object.
 */
@Composable
fun DepthOfFieldSection(
    camera: CameraSnapshot,
    selected: ObjectSummary?,
    onChange: (Camera.() -> Unit) -> Unit,
    onFocusOn: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastAperture by remember { mutableDoubleStateOf(FIRST_APERTURE) }
    LaunchedEffect(camera.aperture) { if (camera.aperture > 0) lastAperture = camera.aperture }
    val on = camera.aperture > 0
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("Depth of field")
            Spacer(Modifier.weight(1f))
            Switch(checked = on, onCheckedChange = { onChange { aperture = if (it) lastAperture else 0.0 } })
        }
        if (!on) {
            Text(
                "Pinhole camera: everything is sharp. Turn on to blur what is off the focus plane.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        ApertureRow(camera.aperture, onChange)
        FocusRow(camera.focusDistance, onChange)
        OutlinedButton(
            onClick = { selected?.let { onFocusOn(it.id) } },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                selected?.let { "Focus on ${it.name}" } ?: "Select an object in Scene",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ApertureRow(value: Double, onChange: (Camera.() -> Unit) -> Unit) {
    var aperture by remember(value) { mutableDoubleStateOf(value) }
    val set: (Double) -> Unit = {
        aperture = it
        onChange { this.aperture = it }
    }
    Column {
        ValueRow("Aperture", Lens.blur(aperture)) {
            NumberField(aperture, onValid = set, range = APERTURE_RANGE, decimals = 2)
        }
        Slider(
            value = aperture.toFloat(),
            onValueChange = { set(it.toDouble()) },
            valueRange = APERTURE_RANGE.start.toFloat()..APERTURE_RANGE.endInclusive.toFloat(),
        )
    }
}

@Composable
private fun FocusRow(value: Double, onChange: (Camera.() -> Unit) -> Unit) {
    var focus by remember(value) { mutableDoubleStateOf(value) }
    val set: (Double) -> Unit = {
        focus = it
        onChange { focusDist = it }
    }
    Column {
        ValueRow("Focus distance", null) {
            NumberField(focus, onValid = set, range = FOCUS_FIELD_RANGE, decimals = 2)
        }
        Slider(
            value = focus.toFloat().coerceIn(FOCUS_SLIDER_RANGE),
            onValueChange = { set(it.toDouble()) },
            valueRange = FOCUS_SLIDER_RANGE,
        )
    }
}

@Composable
private fun ValueRow(label: String, note: String?, field: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        note?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
        }
        field()
    }
}
