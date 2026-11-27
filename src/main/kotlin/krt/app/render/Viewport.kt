package krt.app.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import krt.app.shell.OverlayChip
import krt.app.shell.ViewportBackground
import krt.platform.layout.FrameFit
import krt.platform.layout.FramePlacement
import krt.platform.layout.FrameScaling
import krt.platform.skia.DrawnOnceShader
import org.jetbrains.skia.Shader

/**
 * The frame on a dark background: the CPU frame as pixels, or the GPU frame drawn by its shader
 * (ADR 0009) once at its own size; fitted to the viewport or at its pixel size. A drag over the viewport
 * rotates the camera by the distance in frame pixels, so rotation feels the same at any scale; key presses
 * go to [onKey].
 */
@Composable
fun Viewport(
    image: ImageBitmap?,
    gpuFrame: Shader?,
    frameSize: IntSize,
    modeTitle: String,
    onRotate: (Int, Int) -> Unit,
    onKey: (Key) -> Unit,
    onGpuFrameDraw: (Shader, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    var scaling by remember { mutableStateOf(FrameScaling.FIT) }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(ViewportBackground)
            .clipToBounds()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                onKey(event.key)
                true
            },
    ) {
        val placement =
            FrameFit.place(frameSize.width, frameSize.height, constraints.maxWidth, constraints.maxHeight, scaling)
        // A new picture on every draw would defeat Skia's cache, so it is kept until the frame or its scale changes.
        val shownGpuFrame = remember(gpuFrame, frameSize, placement.scale) {
            gpuFrame?.let { DrawnOnceShader.of(it, frameSize.width, frameSize.height, placement.scale) }
        }
        var dragFrom by remember { mutableStateOf(IntOffset.Zero) }
        Canvas(
            Modifier.fillMaxSize().pointerInput(placement) {
                detectDragGestures(
                    onDragStart = { start ->
                        focusRequester.requestFocus()
                        dragFrom = placement.framePoint(start)
                    },
                    onDrag = { change, _ ->
                        val to = placement.framePoint(change.position)
                        onRotate(to.x - dragFrom.x, to.y - dragFrom.y)
                        dragFrom = to
                        change.consume()
                    },
                )
            },
        ) { drawFrame(image, shownGpuFrame, frameSize, placement) }
        Row(Modifier.align(Alignment.TopStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OverlayChip(modeTitle)
            OverlayChip("${frameSize.width} × ${frameSize.height}")
        }
        ScalingToggle(scaling, onSelect = { scaling = it }, Modifier.align(Alignment.TopEnd).padding(12.dp))
        OverlayChip(
            "Drag — rotate · W A S D — move · R / F — up / down",
            Modifier.align(Alignment.BottomStart).padding(12.dp),
        )
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val currentOnGpuFrameDraw by rememberUpdatedState(onGpuFrameDraw)
    val currentGpuFrame by rememberUpdatedState(gpuFrame)
    LaunchedEffect(Unit) {
        // A new GPU frame is drawn in the window frame that follows; its length is what drawing it cost. Frames that
        // come while one is measured are skipped, so a camera moved every frame still gets measured.
        snapshotFlow { currentGpuFrame }.filterNotNull().collect { shader ->
            val drawn = withFrameNanos { it }
            val next = withFrameNanos { it }
            currentOnGpuFrameDraw(shader, (next - drawn) / NS_PER_MS)
        }
    }
}

@Composable
private fun ScalingToggle(scaling: FrameScaling, onSelect: (FrameScaling) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.height(32.dp)) {
        FrameScaling.entries.forEach { entry ->
            SegmentedButton(
                selected = entry == scaling,
                onClick = { onSelect(entry) },
                shape = SegmentedButtonDefaults.itemShape(entry.ordinal, FrameScaling.entries.size),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    inactiveContainerColor = ViewportBackground.copy(alpha = 0.7f),
                    inactiveContentColor = Color.White.copy(alpha = 0.85f),
                ),
            ) { Text(if (entry == FrameScaling.FIT) "Fit" else "1:1") }
        }
    }
}

private const val NS_PER_MS = 1e6

private fun FramePlacement.framePoint(offset: Offset) = IntOffset(frameX(offset.x), frameY(offset.y))

private fun DrawScope.drawFrame(image: ImageBitmap?, gpuFrame: Shader?, frameSize: IntSize, placement: FramePlacement) {
    if (gpuFrame != null) {
        translate(placement.left, placement.top) {
            val size = Size(frameSize.width * placement.scale, frameSize.height * placement.scale)
            drawRect(ShaderBrush(gpuFrame.asComposeShader()), size = size)
        }
        return
    }
    if (image == null) return
    drawImage(
        image,
        dstOffset = IntOffset(placement.left.toInt(), placement.top.toInt()),
        dstSize = IntSize((image.width * placement.scale).toInt(), (image.height * placement.scale).toInt()),
        filterQuality = FilterQuality.Low,
    )
}
