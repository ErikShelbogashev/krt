package krt.app.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import krt.domain.camera.Camera
import krt.domain.camera.model.CameraSnapshot
import krt.domain.camera.model.ViewPreset
import krt.domain.scene.model.ObjectSummary

/**
 * Camera tab of the inspector: where the camera stands, its lens, depth of field and how it moves.
 * Every value comes from [camera], a snapshot taken after each drawn frame, so the tab follows keyboard and mouse
 * moves too; [selected] is the object picked in the scene tab, for focusing on it.
 */
@Composable
fun CameraTab(
    camera: CameraSnapshot,
    selected: ObjectSummary?,
    onChange: (Camera.() -> Unit) -> Unit,
    onPreset: (ViewPreset) -> Unit,
    onReset: () -> Unit,
    onFocusOn: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ViewSection(camera, onPreset, onReset)
        Divider()
        LensSection(camera, onChange)
        Divider()
        DepthOfFieldSection(camera, selected, onChange, onFocusOn)
        Divider()
        NavigationSection(camera.movementSpeed, onChange)
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
}
