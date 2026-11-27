package krt.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val TABS = listOf("Render", "Camera", "Scene")

/** Right panel of the window: tabs with the render, camera and scene settings. */
@Composable
fun Inspector(
    renderTab: @Composable () -> Unit,
    cameraTab: @Composable () -> Unit,
    sceneTab: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableIntStateOf(0) }
    val background = MaterialTheme.colorScheme.surfaceContainerLow
    Column(modifier.width(360.dp).fillMaxHeight().background(background)) {
        PrimaryTabRow(selectedTabIndex = selected, containerColor = background) {
            TABS.forEachIndexed { index, title ->
                Tab(selected = selected == index, onClick = { selected = index }, text = { Text(title) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (selected) {
                0 -> renderTab()
                1 -> cameraTab()
                else -> sceneTab()
            }
        }
    }
}
