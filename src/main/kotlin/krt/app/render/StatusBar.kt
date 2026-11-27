package krt.app.render

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import krt.app.shell.MonoValue
import krt.domain.render.model.RenderMode
import krt.domain.render.model.RenderProgress
import krt.domain.render.model.RenderStatus

/** Bottom bar: progress of the frame on screen and what draws it. */
@Composable
fun StatusBar(mode: RenderMode, progress: RenderProgress, threads: Int, depth: Int, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LinearProgressIndicator(
            progress = { progress.fraction.toFloat() },
            modifier = Modifier.width(160.dp),
            drawStopIndicator = {},
        )
        Text(RenderStatus.progressText(mode, progress), style = MaterialTheme.typography.labelMedium, color = muted)
        MonoValue(RenderStatus.elapsedText(progress), color = muted)
        Spacer(Modifier.weight(1f))
        Text(
            RenderStatus.engineText(mode, threads, depth),
            style = MaterialTheme.typography.labelMedium,
            color = muted,
        )
    }
}
