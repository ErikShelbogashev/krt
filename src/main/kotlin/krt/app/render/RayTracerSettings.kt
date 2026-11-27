package krt.app.render

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import krt.domain.render.RayTracer

@Composable
fun RayTracerSettings(rayTracer: RayTracer, onChange: (RayTracer.() -> Unit) -> Unit) {
    Column(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Ray Tracer Settings", style = MaterialTheme.typography.h6)

        // Maximum Depth Slider
        var maxDepth by remember { mutableStateOf(rayTracer.maxDepth.toFloat()) }
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