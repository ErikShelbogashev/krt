package krt.app.scene

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import krt.platform.math.Point3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectController(
    speed: Double,
    loadObjectIds: () -> List<String>,
    onMove: (String, Point3) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    val objectIds = remember { mutableStateListOf<String>() }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        objectIds.clear()
        objectIds.addAll(loadObjectIds())
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selectedId ?: "",
                onValueChange = { },
                readOnly = true,
                label = { Text("Select Object ID") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                objectIds.forEach { id ->
                    DropdownMenuItem(
                        text = { Text(id) },
                        onClick = {
                            selectedId = id
                            expanded = false
                        },
                    )
                }
            }
        }
        ControlPanel(selectedId, speed, onMove)
    }
}

private fun Offset.coerceInCircle(center: Offset, radius: Float): Offset {
    val dx = x - center.x
    val dy = y - center.y
    return if (sqrt(dx * dx + dy * dy) <= radius) {
        this
    } else {
        val angle = atan2(dy, dx)
        Offset(
            x = center.x + cos(angle) * radius,
            y = center.y + sin(angle) * radius
        )
    }
}

private fun getDirection(center: Offset, touch: Offset): Point3 {
    val dx = touch.x - center.x
    val dy = touch.y - center.y
    return when {
        abs(dx) > abs(dy) && dx > 0 -> Point3(0.02, 0.0, 0.0) // Right
        abs(dx) > abs(dy) && dx < 0 -> Point3(-0.02, 0.0, 0.0) // Left
        abs(dy) > abs(dx) && dy > 0 -> Point3(0.0, 0.0, 0.02) // Down
        abs(dy) > abs(dx) && dy < 0 -> Point3(0.0, 0.0, -0.02) // Up
        else -> Point3(0.0, 0.0, 0.0) // Center or negligible movement
    }
}

@Composable
private fun ControlPanel(selectedId: String?, speed: Double, onMove: (String, Point3) -> Unit) {
    val canvasSize = 150.dp
    val joystickRadius = canvasSize / 2
    var touchPosition by remember { mutableStateOf(Offset.Zero) }
    var realPosition by remember { mutableStateOf(Offset.Zero) }
    var centerPosition by remember { mutableStateOf(Offset.Zero) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { selectedId?.let { onMove(it, Point3(0.0, speed, 0.0)) } }) {
            Text("Up")
        }

        Box(contentAlignment = Alignment.Center, modifier = Modifier
            .size(canvasSize)
            .onGloballyPositioned { layoutCoordinates ->
                centerPosition = layoutCoordinates.size.toSize().let {
                    Offset(it.width / 2f, it.height / 2f)
                }
                touchPosition = centerPosition
                realPosition = touchPosition
            }
            .pointerInput(selectedId) {
                detectDragGestures(onDragEnd = {
                    touchPosition = centerPosition; realPosition = centerPosition
                }) { change, dragAmount ->
                    val newPosition = realPosition + dragAmount
                    touchPosition = newPosition.coerceInCircle(centerPosition, joystickRadius.value / 2)
                    realPosition = newPosition
                    val direction = getDirection(centerPosition, touchPosition)
                    selectedId?.let { onMove(it, direction) }
                    change.consume()
                }
            }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(color = Color.Gray, center = centerPosition, radius = joystickRadius.toPx() / 2)
                drawCircle(color = Color.DarkGray, center = touchPosition, radius = joystickRadius.toPx() / 4)
            }
        }

        Button(onClick = { selectedId?.let { onMove(it, Point3(0.0, -speed, 0.0)) } }) {
            Text("Down")
        }
    }
}
