package krt.app.render

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import java.io.File
import krt.domain.render.model.Benchmark
import krt.domain.render.model.BenchmarkResult

private val HEADER = listOf(
    "Scene", "Mode", "Size", "FPS", "p50", "p95", "p99", "max", ">50", ">100", "Coverage", "Passes", "Full", "Done",
)
private val WIDTHS = listOf(84, 120, 84, 52, 48, 48, 48, 56, 40, 44, 64, 56, 48, 64)

/**
 * The results of the flight benchmark, a row per run: window frames per second, frame times in ms, stalls,
 * how complete the frame on screen was and how long the last frame took to finish.
 */
@Suppress("DEPRECATION") // LocalClipboardManager: the plain text clipboard is all this needs
@Composable
fun BenchmarkResultsDialog(
    results: List<BenchmarkResult>,
    onSaveCsv: (File) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val saver = rememberFileSaverLauncher(FileKitDialogSettings.createDefault()) { file ->
        file?.let { onSaveCsv(it.file) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text("Flight benchmark results") },
        text = {
            Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TableRow(HEADER, header = true)
                results.forEach { TableRow(Benchmark.cells(it), header = false) }
                Text(
                    "Frame times of the window in ms. Coverage: share of the frame that showed the current " +
                        "view when the camera moved on, a quick look counting as all of it. Passes: passes done " +
                        "by then, a quick look counting as none. Full: share of frames past their quick look. " +
                        "Done: time to the finished frame.",
                    modifier = Modifier.padding(top = 8.dp).width(WIDTHS.sum().dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { clipboard.setText(AnnotatedString(Benchmark.markdown(results))) }) {
                    Text("Copy as Markdown")
                }
                TextButton(onClick = { saver.launch(suggestedName = "krt-benchmark", extension = "csv") }) {
                    Text("Save CSV")
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.background(if (header) colors.surfaceContainerHighest else colors.surfaceContainerHigh)) {
        cells.zip(WIDTHS).forEach { (cell, width) ->
            Text(
                cell,
                modifier = Modifier.width(width.dp).padding(horizontal = 4.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = if (header) FontFamily.Default else FontFamily.Monospace,
                color = if (header) colors.onSurfaceVariant else colors.onSurface,
                maxLines = 1,
            )
        }
    }
}
