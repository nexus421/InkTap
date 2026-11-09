package bayern.kickner.appothek.core

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolColorsTest {

    private val colors = ToolColors(
        light = Color(0xFF3B6EF5) to Color(0xFF6A4FF0),
        dark = Color(0xFF2A4FB8) to Color(0xFF4A36B0),
    )

    @Test
    fun `system mode follows the system`() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
    }

    @Test
    fun `fixed modes ignore the system`() {
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
    }

    @Test
    fun `seed is the first colour of the matching pair`() {
        assertEquals(colors.dark.first, colors.seed(dark = true))
        assertEquals(colors.light.first, colors.seed(dark = false))
    }
}
