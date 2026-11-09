package bayern.kickner.appothek.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import bayern.kickner.appothek.LauncherEntries
import bayern.kickner.appothek.R
import bayern.kickner.appothek.core.ThemeMode
import bayern.kickner.appothek.core.Tool
import bayern.kickner.appothek.core.ToolAvailability

/** Settings as a bottom sheet: design, own launcher icons, about. Tool settings stay inside each tool. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    tools: List<Tool>,
    iconRevision: Int,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onOwnIcon: (Tool, Boolean) -> Unit,
    onAbout: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.design),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            val labels = mapOf(
                ThemeMode.SYSTEM to R.string.theme_system,
                ThemeMode.LIGHT to R.string.theme_light,
                ThemeMode.DARK to R.string.theme_dark,
            )
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = mode == themeMode,
                        onClick = { onThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                    ) { Text(stringResource(labels.getValue(mode))) }
                }
            }

            Text(
                stringResource(R.string.own_icons),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 4.dp),
            )
            tools.forEach { tool ->
                val available = tool.availability(context)
                val enabled = remember(iconRevision) { LauncherEntries.isEnabled(context, tool.id) }
                ListItem(
                    colors = transparent,
                    headlineContent = { Text(stringResource(tool.nameRes)) },
                    supportingContent = (available as? ToolAvailability.Unavailable)?.let { { Text(stringResource(it.reasonRes)) } },
                    trailingContent = {
                        Switch(
                            checked = enabled,
                            enabled = available == ToolAvailability.Available,
                            onCheckedChange = { onOwnIcon(tool, it) },
                        )
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ListItem(
                colors = transparent,
                headlineContent = { Text(stringResource(R.string.about)) },
                trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clickableRow(onAbout),
            )
        }
    }
}
