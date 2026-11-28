package krt.app.shell

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import krt.platform.text.NumberInput

/**
 * A small field for a number in [range]: a valid number goes to [onValid] as it is typed, a number out of the range
 * shows an error and changes nothing. When [value] changes elsewhere, the field shows it.
 */
@Composable
fun NumberField(
    value: Double,
    onValid: (Double) -> Unit,
    range: ClosedFloatingPointRange<Double>,
    decimals: Int,
    modifier: Modifier = Modifier,
    format: (Double, Int) -> String = NumberInput::format,
) {
    val formatted = remember(value, decimals, format) { format(value, decimals) }
    var text by remember { mutableStateOf(formatted) }
    LaunchedEffect(value, formatted) {
        if (NumberInput.parse(text, range) != value) text = formatted
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            NumberInput.parse(it, range)?.let(onValid)
        },
        modifier = modifier.width(96.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        isError = NumberInput.parse(text, range) == null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        placeholder = { Text("—") },
    )
}
