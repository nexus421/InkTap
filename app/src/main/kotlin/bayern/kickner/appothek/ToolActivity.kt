package bayern.kickner.appothek

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import bayern.kickner.appothek.core.AppothekTheme
import bayern.kickner.appothek.core.ThemeMode

/**
 * A tool running as its own app: own task (see the manifest), no back arrow, back closes it.
 * Subclasses only pick the tool, see the `tool` package.
 */
abstract class ToolActivity(private val toolId: String) : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val tool = toolById(toolId) ?: return finish()
        val settings = AppSettings.from(this)
        setContent {
            val mode by settings.themeMode.collectAsState(ThemeMode.SYSTEM)
            SystemBars(mode.isDark(isSystemInDarkTheme()))
            AppothekTheme(mode, accent = tool.colors) { tool.Content(onBack = null) }
        }
    }
}
