package krt.app.render

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import krt.app.shell.KrtIcons
import krt.app.shell.SectionHeader
import krt.app.shell.SliderRow
import krt.domain.camera.Camera
import krt.domain.render.RayTracer
import krt.domain.render.RenderController

/** Render tab of the inspector: quality, resolution and, folded away, the scheduler of the CPU workers. */
@Composable
fun RenderTab(
    camera: Camera,
    rayTracer: RayTracer,
    renderController: RenderController,
    onCameraChange: (Camera.() -> Unit) -> Unit,
    onRayTracerChange: (RayTracer.() -> Unit) -> Unit,
    onRenderChange: (RenderController.() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader("Quality")
        QualitySection(camera, rayTracer, onCameraChange, onRayTracerChange)
        SectionHeader("Resolution")
        ResolutionSection(
            width = camera.imageWidth,
            height = camera.imageHeight,
            onResize = { width, height -> onCameraChange { imageWidth = width; imageHeight = height } },
        )
        SchedulerSection(renderController, onRenderChange)
    }
}

@Composable
private fun QualitySection(
    camera: Camera,
    rayTracer: RayTracer,
    onCameraChange: (Camera.() -> Unit) -> Unit,
    onRayTracerChange: (RayTracer.() -> Unit) -> Unit,
) {
    var samples by remember { mutableFloatStateOf(camera.samplesPerPixel.toFloat()) }
    SliderRow(
        label = "Samples per pixel",
        valueText = "${samples.toInt()}",
        value = samples,
        onValueChange = {
            samples = it
            onCameraChange { samplesPerPixel = it.toInt() }
        },
        range = 1f..50f,
        steps = 48,
    )
    var maxDepth by remember { mutableFloatStateOf(rayTracer.maxDepth.toFloat()) }
    SliderRow(
        label = "Max bounces",
        valueText = "${maxDepth.toInt()}",
        value = maxDepth,
        onValueChange = {
            maxDepth = it
            onRayTracerChange { this.maxDepth = it.toInt() }
        },
        range = 1f..20f,
        steps = 18,
    )
}

@Composable
private fun SchedulerSection(
    renderController: RenderController,
    onRenderChange: (RenderController.() -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { expanded = !expanded },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionHeader("Scheduler (advanced)")
        Spacer(Modifier.weight(1f))
        Icon(
            if (expanded) KrtIcons.ExpandLess else KrtIcons.ExpandMore,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (!expanded) return
    var repaintInterval by remember { mutableFloatStateOf(renderController.repaintIntervalMs.toFloat()) }
    SliderRow(
        label = "Repaint interval",
        valueText = "${repaintInterval.toInt()} ms",
        value = repaintInterval,
        onValueChange = {
            repaintInterval = it
            onRenderChange { repaintIntervalMs = it.toLong() }
        },
        range = 10f..200f,
        steps = 18,
    )
    var batchSize by remember { mutableFloatStateOf(renderController.batchSize.toFloat()) }
    SliderRow(
        label = "Batch size",
        valueText = "${batchSize.toInt()} px",
        value = batchSize,
        onValueChange = {
            batchSize = it
            onRenderChange { this.batchSize = it.toInt() }
        },
        range = 1f..100f,
        steps = 98,
    )
}
