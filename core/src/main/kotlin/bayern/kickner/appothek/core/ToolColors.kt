package bayern.kickner.appothek.core

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** The fixed colours of a tool: a two-stop gradient for light and a deeper one for dark mode. */
data class ToolColors(val light: Pair<Color, Color>, val dark: Pair<Color, Color>) {

    fun gradient(dark: Boolean): Brush = pair(dark).let { (start, end) -> Brush.linearGradient(listOf(start, end)) }

    /** The primary colour of the tool's theme. */
    fun seed(dark: Boolean): Color = pair(dark).first

    private fun pair(dark: Boolean) = if (dark) this.dark else light
}
