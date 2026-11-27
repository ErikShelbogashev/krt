package krt.app.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import krt.domain.render.RayTracer

@Composable
fun RayTracerSettings(
    rayTracer: RayTracer,
    onChange: (RayTracer.() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Ray Tracer Settings", style = MaterialTheme.typography.h6)

        var maxDepth by remember { mutableFloatStateOf(rayTracer.maxDepth.toFloat()) }
        Text("Max Depth: ${maxDepth.toInt()}")

        Slider(
            value = maxDepth,
            onValueChange = { newValue ->
                maxDepth = newValue
                onChange { this.maxDepth = newValue.toInt() }
            },
            valueRange = 1f..20f,
            steps = 19,
            modifier = Modifier.width(200.dp)
        )
    }
}
