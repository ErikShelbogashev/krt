package krt.app.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC3B5FF),
    onPrimary = Color(0xFF2B1A73),
    primaryContainer = Color(0xFF42348F),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondaryContainer = Color(0xFF3B3754),
    onSecondaryContainer = Color(0xFFE6DEFF),
    background = Color(0xFF121316),
    onBackground = Color(0xFFE4E2E9),
    surface = Color(0xFF121316),
    onSurface = Color(0xFFE4E2E9),
    surfaceVariant = Color(0xFF26272C),
    onSurfaceVariant = Color(0xFFA9A8B3),
    surfaceContainerLowest = Color(0xFF0D0E10),
    surfaceContainerLow = Color(0xFF1B1C20),
    surfaceContainer = Color(0xFF1B1C20),
    surfaceContainerHigh = Color(0xFF26272C),
    surfaceContainerHighest = Color(0xFF303137),
    outline = Color(0xFF3A3B42),
    outlineVariant = Color(0xFF3A3B42),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5646C0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5DEFF),
    onPrimaryContainer = Color(0xFF180A6B),
    secondaryContainer = Color(0xFFE4DFF9),
    onSecondaryContainer = Color(0xFF1C192E),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE5E1EC),
    onSurfaceVariant = Color(0xFF5E5D69),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F2FA),
    surfaceContainer = Color(0xFFEFECF5),
    surfaceContainerHigh = Color(0xFFE9E6EF),
    surfaceContainerHighest = Color(0xFFE3E1EA),
    outline = Color(0xFFCAC6D3),
    outlineVariant = Color(0xFFCAC6D3),
)

/** Dark background of the viewport in both themes: the frame is judged against black. */
val ViewportBackground = Color(0xFF0B0C0E)

/** Material 3 theme of the window, dark or light. */
@Composable
fun KrtTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
