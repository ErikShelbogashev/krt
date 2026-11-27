package krt.app.scene

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import java.io.File

@Composable
fun LoadObject(onFilePicked: (File) -> Unit, modifier: Modifier = Modifier) {
    val launcher = rememberFilePickerLauncher(type = FileKitType.File(extensions = listOf("obj"))) { platformFile ->
        platformFile?.let { onFilePicked(it.file) }
    }
    Button(onClick = { launcher.launch() }, modifier = modifier) {
        Text("Load File")
    }
}
