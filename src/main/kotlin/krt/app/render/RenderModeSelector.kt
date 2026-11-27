package krt.app.render

import androidx.compose.foundation.layout.width
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import krt.domain.render.model.RenderMode

/** Segmented button that picks the renderer. */
@Composable
fun RenderModeSelector(mode: RenderMode, onSelect: (RenderMode) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.width(450.dp)) {
        RenderMode.entries.forEach { entry ->
            SegmentedButton(
                selected = entry == mode,
                onClick = { onSelect(entry) },
                shape = SegmentedButtonDefaults.itemShape(entry.ordinal, RenderMode.entries.size),
                icon = {},
            ) { Text(entry.title, maxLines = 1) }
        }
    }
}
