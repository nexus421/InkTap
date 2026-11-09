package bayern.kickner.appothek.core

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

private val Light = lightColorScheme(
    primary = Color(0xFF3D4A63),
    background = Color(0xFFF7F7F8),
    surface = Color(0xFFF7F7F8),
    surfaceContainerLow = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1B1B1F),
    onSurface = Color(0xFF1B1B1F),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB8C4DE),
    background = Color(0xFF111214),
    surface = Color(0xFF111214),
    surfaceContainerLow = Color(0xFF1C1D21),
    onBackground = Color(0xFFE6E6EA),
    onSurface = Color(0xFFE6E6EA),
)

/**
 * The Appothek theme. Fixed colours, no dynamic colour: every tool keeps its own [accent] so it looks the same
 * inside the Appothek and as its own app.
 */
@Composable
fun AppothekTheme(themeMode: ThemeMode, accent: ToolColors? = null, content: @Composable () -> Unit) {
    val dark = themeMode.isDark(isSystemInDarkTheme())
    val base = if (dark) Dark else Light
    MaterialTheme(colorScheme = accent?.let { base.withAccent(it, dark) } ?: base, content = content)
}

private fun ColorScheme.withAccent(accent: ToolColors, dark: Boolean): ColorScheme {
    val seed = accent.seed(dark)
    val container = lerp(seed, background, if (dark) 0.55f else 0.8f)
    return copy(
        primary = if (dark) lerp(seed, Color.White, 0.35f) else seed,
        onPrimary = if (dark) Color(0xFF111214) else Color.White,
        primaryContainer = container,
        onPrimaryContainer = onBackground,
        secondaryContainer = container,
        onSecondaryContainer = onBackground,
    )
}
