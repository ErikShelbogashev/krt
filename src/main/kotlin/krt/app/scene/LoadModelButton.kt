package krt.app.scene

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import java.io.File

/** Button that opens an .obj file picker and hands the picked file to [onOpen]. */
@Composable
fun LoadModelButton(onOpen: (File) -> Unit, modifier: Modifier = Modifier) {
    val launcher = rememberFilePickerLauncher(type = FileKitType.File(extensions = listOf("obj"))) { platformFile ->
        platformFile?.let { onOpen(it.file) }
    }
    OutlinedButton(onClick = { launcher.launch() }, modifier = modifier) {
        Text("Open .obj…")
    }
}
