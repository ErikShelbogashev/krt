package krt

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import krt.infra.AppContext

fun main() {
    val app = AppContext.create()
    application {
        Window(
            onCloseRequest = {
                app.close()
                exitApplication()
            },
            title = "krt",
            state = rememberWindowState(size = DpSize(1360.dp, 860.dp)),
        ) {
            KrtApp(app)
        }
    }
}
