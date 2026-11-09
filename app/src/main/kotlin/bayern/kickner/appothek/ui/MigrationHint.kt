package bayern.kickner.appothek.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import bayern.kickner.appothek.R
import bayern.kickner.appothek.core.Tool

/** Asked once after the update from the Buchstabierer app. [onAnswer] gets true for "own icon, please". */
@Composable
fun MigrationHint(tool: Tool, onAnswer: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.appothek_migration_title, stringResource(tool.nameRes))) },
        text = { Text(stringResource(R.string.appothek_migration_text)) },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text(stringResource(R.string.appothek_yes)) } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text(stringResource(R.string.appothek_no)) } },
    )
}
