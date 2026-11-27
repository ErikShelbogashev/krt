package krt.app.scene

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import java.io.File

/** Button that opens a scene file picker (pbrt-v4 or OBJ) and hands the picked file to [onOpen]. */
@Composable
fun OpenSceneButton(onOpen: (File) -> Unit, modifier: Modifier = Modifier) {
    val type = FileKitType.File(extensions = listOf("pbrt", "obj"))
    val launcher = rememberFilePickerLauncher(type = type) { platformFile ->
        platformFile?.let { onOpen(it.file) }
    }
    OutlinedButton(onClick = { launcher.launch() }, modifier = modifier) {
        Text("Open scene…")
    }
}
