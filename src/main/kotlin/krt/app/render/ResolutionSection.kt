package krt.app.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import krt.domain.camera.Camera

private val SIZE_HINT = "From ${Camera.IMAGE_SIZE_RANGE.first} to ${Camera.IMAGE_SIZE_RANGE.last}"

private val PRESETS = listOf(IntSize(400, 400), IntSize(640, 360), IntSize(800, 600), IntSize(1024, 1024))

/**
 * Size of the rendered frame, independent from the window: presets and two fields. A field keeps a value
 * outside [Camera.IMAGE_SIZE_RANGE] on screen with an error, the frame keeps the last valid size.
 */
@Composable
fun ResolutionSection(width: Int, height: Int, onResize: (Int, Int) -> Unit, modifier: Modifier = Modifier) {
    var size by remember { mutableStateOf(IntSize(width, height)) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PRESETS.forEach { preset ->
                FilterChip(
                    selected = size == preset,
                    onClick = {
                        size = preset
                        onResize(preset.width, preset.height)
                    },
                    label = { Text("${preset.width}×${preset.height}") },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SizeField(
                label = "Width",
                value = size.width,
                onValid = {
                    size = IntSize(it, size.height)
                    onResize(it, size.height)
                },
                modifier = Modifier.weight(1f),
            )
            SizeField(
                label = "Height",
                value = size.height,
                onValid = {
                    size = IntSize(size.width, it)
                    onResize(size.width, it)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SizeField(label: String, value: Int, onValid: (Int) -> Unit, modifier: Modifier = Modifier) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val invalid = Camera.parseImageSize(text) == null
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            Camera.parseImageSize(it)?.let(onValid)
        },
        modifier = modifier,
        label = { Text(label) },
        suffix = { Text("px") },
        isError = invalid,
        supportingText = { Text(if (invalid) SIZE_HINT else " ") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
