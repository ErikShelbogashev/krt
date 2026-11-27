package krt.app.render

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import java.io.File

/**
 * Button that asks where to save the frame as PNG and hands the chosen file to [onSave].
 * Off in GPU mode: the GPU frame never reaches the CPU pixels (ADR 0009).
 */
@Composable
fun SaveImageButton(onSave: (File) -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val launcher = rememberFileSaverLauncher(FileKitDialogSettings.createDefault()) { platformFile ->
        platformFile?.let { onSave(it.file) }
    }
    OutlinedButton(
        onClick = { launcher.launch(suggestedName = "krt-frame", extension = "png") },
        enabled = enabled,
        modifier = modifier,
    ) {
        Text("Save image…")
    }
}
