package bayern.kickner.appothek.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import bayern.kickner.appothek.LauncherEntries
import bayern.kickner.appothek.R
import bayern.kickner.appothek.core.ThemeMode
import bayern.kickner.appothek.core.Tool
import bayern.kickner.appothek.core.ToolAvailability
import kotlinx.coroutines.launch

/** Fixed grid, always two cards per row. Cards never move: new tools are appended. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartScreen(
    tools: List<Tool>,
    dark: Boolean,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onOpen: (Tool) -> Unit,
    onAbout: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // Bumped after a launcher icon was switched, so cards and switches read the new state.
    var iconRevision by remember { mutableIntStateOf(0) }

    // Only the drawer entry; the homescreen pin is offered once, in the migration hint.
    fun setOwnIcon(tool: Tool, enabled: Boolean) {
        LauncherEntries.setEnabled(context, tool, enabled)
        iconRevision++
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.appothek_settings))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tools, key = { it.id }) { tool ->
                val availability = tool.availability(context)
                ToolCard(
                    tool = tool,
                    dark = dark,
                    available = availability == ToolAvailability.Available,
                    // Read when the menu opens: the migration hint may have changed it from outside this screen.
                    isOwnIcon = { LauncherEntries.isEnabled(context, tool.id) },
                    onClick = {
                        if (availability is ToolAvailability.Unavailable) {
                            scope.launch { snackbar.showSnackbar(context.getString(availability.reasonRes)) }
                        } else {
                            onOpen(tool)
                        }
                    },
                    onToggleOwnIcon = { setOwnIcon(tool, !LauncherEntries.isEnabled(context, tool.id)) },
                )
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            tools = tools,
            iconRevision = iconRevision,
            themeMode = themeMode,
            onThemeMode = onThemeMode,
            onOwnIcon = ::setOwnIcon,
            onAbout = {
                showSettings = false
                onAbout()
            },
            onDismiss = { showSettings = false },
        )
    }
}
