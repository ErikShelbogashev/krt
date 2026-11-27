package krt.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Top bar of the window: title, [modeSelector], [actions] on the right, restart and theme buttons at the end.
 */
@Composable
fun TopBar(
    onRestart: () -> Unit,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    modeSelector: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("krt", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "ray tracer",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        modeSelector()
        Spacer(Modifier.weight(1f))
        actions()
        FilledIconButton(onClick = onRestart) { Icon(KrtIcons.Refresh, contentDescription = "Restart render") }
        IconButton(onClick = onToggleTheme) {
            Icon(
                if (dark) KrtIcons.LightMode else KrtIcons.DarkMode,
                contentDescription = if (dark) "Light theme" else "Dark theme",
            )
        }
    }
}
