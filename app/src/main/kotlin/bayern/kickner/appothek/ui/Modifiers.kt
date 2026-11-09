package bayern.kickner.appothek.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier

internal fun Modifier.clickableRow(onClick: () -> Unit): Modifier = clickable(onClick = onClick)
