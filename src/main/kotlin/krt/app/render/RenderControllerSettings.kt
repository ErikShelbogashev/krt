package krt.app.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import krt.domain.render.RenderController

@Composable
fun RenderControllerSettings(
    renderController: RenderController,
    onChange: (RenderController.() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Render Controller Settings", style = MaterialTheme.typography.titleLarge)

        var repaintIntervalMs by remember { mutableFloatStateOf(renderController.repaintIntervalMs.toFloat()) }
        Text("Repaint Interval (ms): ${repaintIntervalMs.toInt()}")

        Slider(
            value = repaintIntervalMs,
            onValueChange = { newValue ->
                repaintIntervalMs = newValue
                onChange { this.repaintIntervalMs = newValue.toLong() }
            },
            valueRange = 10f..200f,
            steps = 19,
            modifier = Modifier.width(200.dp)
        )

        var batchSize by remember { mutableFloatStateOf(renderController.batchSize.toFloat()) }
        Text("Batch Size: ${batchSize.toInt()}")

        Slider(
            value = batchSize,
            onValueChange = { newValue ->
                batchSize = newValue
                onChange { this.batchSize = newValue.toInt() }
            },
            valueRange = 1f..100f,
            steps = 99,
            modifier = Modifier.width(200.dp)
        )
    }
}
