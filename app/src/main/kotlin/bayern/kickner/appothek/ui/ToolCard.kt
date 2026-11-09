package bayern.kickner.appothek.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import bayern.kickner.appothek.R
import bayern.kickner.appothek.core.Tool

/** Square card: gradient in the tool's colour, name and one line top left, large faded motif bottom right. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolCard(
    tool: Tool,
    dark: Boolean,
    available: Boolean,
    isOwnIcon: () -> Boolean,
    onClick: () -> Unit,
    onToggleOwnIcon: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var ownIcon by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .alpha(if (available) 1f else 0.4f)
                .clip(RoundedCornerShape(24.dp))
                .background(tool.colors.gradient(dark))
                .combinedClickable(onClick = onClick, onLongClick = {
                    if (available) {
                        ownIcon = isOwnIcon()
                        menu = true
                    }
                }),
        ) {
            Image(
                painter = painterResource(tool.motifRes),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(150.dp)
                    .offset(x = 30.dp, y = 30.dp)
                    .alpha(0.24f),
            )
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(tool.nameRes),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(tool.descriptionRes),
                    color = Color.White.copy(alpha = 0.88f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (ownIcon) R.string.appothek_own_icon_remove else R.string.appothek_own_icon_add)) },
                onClick = {
                    menu = false
                    onToggleOwnIcon()
                },
            )
        }
    }
}
