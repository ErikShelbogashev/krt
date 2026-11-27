package krt.app.scene

import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import java.io.File

@Composable
fun LoadObject(onFilePicked: (File) -> Unit) {
    val launcher = rememberFilePickerLauncher(type = FileKitType.File(extensions = listOf("obj"))) { platformFile ->
        platformFile?.let { onFilePicked(it.file) }
    }
    Button(onClick = { launcher.launch() }) {
        Text("Load File")
    }
}
