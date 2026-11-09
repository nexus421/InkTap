package bayern.kickner.appothek

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import bayern.kickner.appothek.core.AppothekTheme
import bayern.kickner.appothek.core.ThemeMode
import bayern.kickner.appothek.ui.AboutScreen
import bayern.kickner.appothek.ui.MigrationHint
import bayern.kickner.appothek.ui.StartScreen
import bayern.kickner.buchstabierer.BuchstabiererTool
import bayern.kickner.buchstabierer.LegacyStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The Appothek: start screen with all tools. A tool opened here runs inside this task, back returns to the grid. */
class MainActivity : ComponentActivity() {

    private val settings by lazy { AppSettings.from(this) }
    private var showMigrationHint by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        LauncherEntries.sync(this, tools)
        Shortcuts.update(this, tools)
        checkMigrationHint()

        setContent {
            val mode by settings.themeMode.collectAsState(ThemeMode.SYSTEM)
            val dark = mode.isDark(isSystemInDarkTheme())
            val scope = rememberCoroutineScope()
            var openToolId by rememberSaveable { mutableStateOf<String?>(null) }
            var showAbout by rememberSaveable { mutableStateOf(false) }
            val openTool = toolById(openToolId)
            SystemBars(dark)

            when {
                openTool != null -> {
                    BackHandler { openToolId = null }
                    AppothekTheme(mode, accent = openTool.colors) { openTool.Content(onBack = { openToolId = null }) }
                }
                showAbout -> AppothekTheme(mode) {
                    BackHandler { showAbout = false }
                    AboutScreen(onBack = { showAbout = false })
                }
                else -> AppothekTheme(mode) {
                    StartScreen(
                        tools = tools,
                        dark = dark,
                        themeMode = mode,
                        onThemeMode = { scope.launch { settings.setThemeMode(it) } },
                        onOpen = { openToolId = it.id },
                        onAbout = { showAbout = true },
                    )
                    if (showMigrationHint) {
                        MigrationHint(
                            tool = BuchstabiererTool,
                            onAnswer = { keepOwnIcon ->
                                if (keepOwnIcon) {
                                    LauncherEntries.setEnabled(this, BuchstabiererTool, true)
                                    Shortcuts.requestPin(this, BuchstabiererTool)
                                }
                                showMigrationHint = false
                                scope.launch { settings.setMigrationHintDone() }
                            },
                        )
                    }
                }
            }
        }
    }

    /**
     * Users who come from the Buchstabierer app get asked once whether it should keep its own icon. On a fresh
     * install the question is marked as done right away, so later Buchstabierer use never triggers it.
     */
    private fun checkMigrationHint() = lifecycleScope.launch {
        if (settings.migrationHintDone.first()) return@launch
        if (LegacyStorage.exists(this@MainActivity)) showMigrationHint = true else settings.setMigrationHintDone()
    }
}
