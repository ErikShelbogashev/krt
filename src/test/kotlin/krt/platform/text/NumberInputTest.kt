package krt.platform.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NumberInputTest {
    @Test
    fun `editable coefficients survive changing the selected object`() {
        for (value in listOf(0.00001, 0.0754, 0.123456789, 1e21, 0.0)) {
            assertEquals(value, NumberInput.editable(value, 4).toDouble())
        }
    }


    @Test
    fun `numbers within the range are accepted, with a dot or a comma`() {
        assertEquals(0.5, NumberInput.parse(" 0.5 ", 0.01..10.0))
        assertEquals(2.25, NumberInput.parse("2,25", 0.01..10.0))
        assertEquals(10.0, NumberInput.parse("10", 0.01..10.0))
    }

    @Test
    fun `numbers out of the range and other text are not`() {
        assertNull(NumberInput.parse("0", 0.01..10.0))
        assertNull(NumberInput.parse("11", 0.01..10.0))
        assertNull(NumberInput.parse("abc", 0.01..10.0))
        assertNull(NumberInput.parse("", 0.01..10.0))
    }

    @Test
    fun `numbers are shown with the given decimals`() {
        assertEquals("0.10", NumberInput.format(0.1, 2))
        assertEquals("90", NumberInput.format(90.0, 0))
    }
}
