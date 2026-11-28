package krt.app.scene

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import krt.app.shell.NumberField
import krt.app.shell.SliderRow
import krt.domain.scene.model.ObjectChange
import krt.platform.text.NumberInput
import krt.domain.scene.model.SelectedOptics

@Composable
fun SamplingControls(
    filtered: Boolean, stratified: Boolean, onFilter: (Boolean) -> Unit, onStratify: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SamplingToggle("Filter distant wood grain", filtered, onFilter)
        SamplingToggle("Stratified pixel sampling", stratified, onStratify)
    }
}

@Composable
private fun SamplingToggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(value, onCheckedChange = null)
    }
}

@Composable
fun ObjectProperties(optics: SelectedOptics, onEdit: (ObjectChange) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        optics.fractal?.let { obj ->
            val range = obj.kind.iterationRange
            SliderRow("Fractal iterations", obj.iterations.toString(), obj.iterations.toFloat(),
                onValueChange = { onEdit(ObjectChange.Iterations(it.roundToInt())) },
                range = range.first.toFloat()..range.last.toFloat(), steps = range.last - range.first - 1)
        }
        optics.emission?.let { scale ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Emission scale", style = MaterialTheme.typography.bodyMedium)
                NumberField(scale, { onEdit(ObjectChange.Emission(it)) },
                    0.0..Float.MAX_VALUE.toDouble(), EMISSION_DECIMALS, format = NumberInput::editable)
            }
        }
        optics.medium?.let { medium ->
            Text("Volume extinction · per scene unit", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ObjectChange.Channel.entries.forEach { channel ->
                    Column(Modifier.weight(1f)) {
                        Text(channel.name, style = MaterialTheme.typography.labelSmall)
                        NumberField(medium.density[channel.ordinal], { onEdit(ObjectChange.Extinction(channel, it)) },
                            0.0..Float.MAX_VALUE.toDouble(), EXTINCTION_DECIMALS, Modifier.fillMaxWidth(),
                            format = NumberInput::editable)
                    }
                }
            }
        }
    }
}

private const val EMISSION_DECIMALS = 3
private const val EXTINCTION_DECIMALS = 4
