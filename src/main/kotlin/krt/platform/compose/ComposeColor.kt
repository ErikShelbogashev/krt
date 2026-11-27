package krt.platform.compose

import androidx.compose.ui.graphics.Color as ComposeColor
import krt.platform.math.Color

/** The linear color as a Compose color for a swatch: channels above 1, like emitted light, are clipped. */
fun Color.toComposeColor(): ComposeColor =
    ComposeColor(x.coerceIn(0.0, 1.0).toFloat(), y.coerceIn(0.0, 1.0).toFloat(), z.coerceIn(0.0, 1.0).toFloat())
