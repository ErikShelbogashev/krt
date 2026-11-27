package krt.app.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.tan
import krt.platform.text.NumberInput

/** Colour of what is in focus in the diagram. */
private val FOCUS = Color(0xFF7BD88F)

/** Focus distances the diagram spans, left to right. */
private const val DIAGRAM_DEPTH = 10.0

/** Wider angles would leave the diagram at once; the wedge stops opening here. */
private const val MAX_DRAWN_HALF_ANGLE = 80.0

/** Dash and gap of the focus line with depth of field off, in pixels. */
private const val DASH = 6f

/**
 * The lens from the side: the camera at the left, the field of view as a wedge, the focus plane as a green line
 * and, with depth of field on, a band around it that widens with the aperture.
 */
@Composable
fun LensDiagram(fov: Double, focusDistance: Double, aperture: Double, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawWedge(fov, primary)
            drawFocus(focusDistance, aperture)
            drawCameraBody(ink)
        }
        DiagramLabel("FOV ${fov.toInt()}°", primary, Modifier.align(Alignment.TopStart))
        DiagramLabel(
            if (aperture > 0) "focus ${NumberInput.format(focusDistance, 1)}" else "all sharp",
            FOCUS,
            Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun DiagramLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall, color = color)
}

private fun DrawScope.lensX() = 30.dp.toPx()

private fun DrawScope.focusX(distance: Double) =
    lensX() + (size.width - lensX() - 12.dp.toPx()) * (distance.coerceIn(0.0, DIAGRAM_DEPTH) / DIAGRAM_DEPTH).toFloat()

private fun DrawScope.drawWedge(fov: Double, color: Color) {
    val half = tan(Math.toRadians((fov / 2).coerceAtMost(MAX_DRAWN_HALF_ANGLE))).toFloat()
    val apex = Offset(lensX(), size.height / 2)
    val reach = size.width - apex.x
    val wedge = Path().apply {
        moveTo(apex.x, apex.y)
        lineTo(size.width, apex.y - half * reach)
        lineTo(size.width, apex.y + half * reach)
        close()
    }
    drawPath(wedge, color.copy(alpha = 0.16f))
    drawPath(wedge, color.copy(alpha = 0.6f), style = Stroke(width = 1.dp.toPx()))
}

private fun DrawScope.drawFocus(distance: Double, aperture: Double) {
    val x = focusX(distance)
    if (aperture > 0) {
        val band = ((size.width - lensX()) * (aperture.coerceAtMost(DIAGRAM_DEPTH) / DIAGRAM_DEPTH) * 0.5).toFloat()
        val width = band + 2.dp.toPx()
        drawRect(FOCUS.copy(alpha = 0.16f), Offset(x - width / 2, 0f), Size(width, size.height))
    }
    drawLine(
        FOCUS,
        Offset(x, 8.dp.toPx()),
        Offset(x, size.height - 8.dp.toPx()),
        strokeWidth = 2.dp.toPx(),
        pathEffect = if (aperture > 0) null else PathEffect.dashPathEffect(floatArrayOf(DASH, DASH)),
    )
}

private fun DrawScope.drawCameraBody(color: Color) {
    val body = 18.dp.toPx()
    drawRoundRect(
        color,
        topLeft = Offset(lensX() - body - 2.dp.toPx(), size.height / 2 - body / 2),
        size = Size(body, body),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
    )
}
