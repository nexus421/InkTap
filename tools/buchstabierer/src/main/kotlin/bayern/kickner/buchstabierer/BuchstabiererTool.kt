package bayern.kickner.buchstabierer

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import bayern.kickner.appothek.core.Tool
import bayern.kickner.appothek.core.ToolAvailability
import bayern.kickner.appothek.core.ToolColors

object BuchstabiererTool : Tool {
    override val id = "buchstabierer"
    override val nameRes = R.string.buchstabierer_name
    override val descriptionRes = R.string.buchstabierer_description
    override val motifRes = R.drawable.motif_buchstabierer
    override val colors = ToolColors(
        light = Color(0xFF3B6EF5) to Color(0xFF6A4FF0),
        dark = Color(0xFF2A4FB8) to Color(0xFF4A36B0),
    )

    override fun availability(context: Context) = ToolAvailability.Available

    @Composable
    override fun Content(onBack: (() -> Unit)?) = BuchstabiererScreen(onBack)
}
