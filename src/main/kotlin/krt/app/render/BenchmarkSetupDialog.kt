package krt.app.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import krt.app.shell.SectionHeader
import krt.domain.render.model.BenchmarkConfig
import krt.domain.render.model.RenderMode
import krt.domain.scene.model.DemoScene

private val SIZES = listOf(400 to 400, 800 to 600, 1024 to 1024)
private val MODES = listOf(RenderMode.CPU, RenderMode.GPU)

/** Chooses what the flight benchmark runs: scenes, renderers and frame sizes, every combination. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BenchmarkSetupDialog(onRun: (List<BenchmarkConfig>) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var scenes by remember { mutableStateOf(setOf(DemoScene.SHOWCASE)) }
    var modes by remember { mutableStateOf(MODES.toSet()) }
    var sizes by remember { mutableStateOf(setOf(SIZES.first())) }
    val plan = BenchmarkConfig.plan(
        DemoScene.entries.filter { it in scenes },
        MODES.filter { it in modes },
        SIZES.filter { it in sizes },
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text("Flight benchmark") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "The camera flies through the scene along a fixed path, one pose per window frame, " +
                        "at ${BenchmarkConfig.SAMPLES} samples and depth ${BenchmarkConfig.DEPTH}. " +
                        "Your settings come back afterwards. Esc stops.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Choices("Scenes", DemoScene.entries, scenes, { it.title }) { scenes = scenes.toggled(it) }
                Choices("Modes", MODES, modes, { it.title }) { modes = modes.toggled(it) }
                Choices("Frame sizes", SIZES, sizes, { "${it.first}×${it.second}" }) { sizes = sizes.toggled(it) }
                Text(
                    "${plan.size} runs of ${BenchmarkConfig.STEPS} poses",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onRun(plan) }, enabled = plan.isNotEmpty()) { Text("Run") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choices(title: String, options: List<T>, chosen: Set<T>, label: (T) -> String, onToggle: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader(title)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                FilterChip(option in chosen, onClick = { onToggle(option) }, label = { Text(label(option)) })
            }
        }
    }
}

private fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item
