package krt.app.scene

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import krt.app.shell.MonoValue
import krt.domain.scene.model.ObjectSummary
import krt.platform.math.Point3

/** Position of the selected object along X, Y and Z, with buttons that step it by [step] either way. */
@Composable
fun MoveCard(summary: ObjectSummary, step: Double, onMove: (String, Point3) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Move · ${summary.name}",
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            AxisControl(
                "X",
                summary.position.x,
                onDecrease = { onMove(summary.id, Point3(-step, 0.0, 0.0)) },
                onIncrease = { onMove(summary.id, Point3(step, 0.0, 0.0)) },
            )
            AxisControl(
                "Y",
                summary.position.y,
                onDecrease = { onMove(summary.id, Point3(0.0, -step, 0.0)) },
                onIncrease = { onMove(summary.id, Point3(0.0, step, 0.0)) },
            )
            AxisControl(
                "Z",
                summary.position.z,
                onDecrease = { onMove(summary.id, Point3(0.0, 0.0, -step)) },
                onIncrease = { onMove(summary.id, Point3(0.0, 0.0, step)) },
            )
        }
        Text(
            "Step = movement speed (${String.format(Locale.ROOT, "%.1f", step)})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AxisControl(axis: String, value: Double, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(axis, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MonoValue(String.format(Locale.ROOT, "%.2f", value))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilledTonalIconButton(onClick = onDecrease, modifier = Modifier.size(32.dp)) { Text("−") }
            FilledTonalIconButton(onClick = onIncrease, modifier = Modifier.size(32.dp)) { Text("+") }
        }
    }
}
