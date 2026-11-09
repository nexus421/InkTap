package bayern.kickner.appothek

import bayern.kickner.appothek.core.Tool
import bayern.kickner.buchstabierer.BuchstabiererTool
import bayern.kickner.inktap.InkTapTool

/**
 * All tools, in start screen order. New tools go to the end, so the cards never move.
 * A new tool also needs its activity and launcher alias in the manifest and an entry in [LauncherEntries].
 */
val tools: List<Tool> = listOf(BuchstabiererTool, InkTapTool)

fun toolById(id: String?): Tool? = tools.firstOrNull { it.id == id }
