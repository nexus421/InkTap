package bayern.kickner.appothek

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import bayern.kickner.appothek.core.Tool
import bayern.kickner.appothek.core.ToolAvailability

/**
 * App shortcuts (long press on the Appothek icon) and pinned tool icons. They are dynamic, not static, because
 * static shortcuts cannot be hidden at runtime and InkTap must not appear without NFC.
 */
object Shortcuts {

    private val icons = mapOf("buchstabierer" to R.mipmap.ic_launcher_buchstabierer, "inktap" to R.mipmap.ic_launcher_inktap)

    fun update(context: Context, tools: List<Tool>) {
        val available = tools.filter { it.availability(context) == ToolAvailability.Available }
        ShortcutManagerCompat.setDynamicShortcuts(context, available.map { info(context, it, "tool_${it.id}") })
    }

    /** Asks the launcher to put the tool's own icon on the homescreen. The system shows a confirmation. */
    fun requestPin(context: Context, tool: Tool): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        return ShortcutManagerCompat.requestPinShortcut(context, info(context, tool, "pin_${tool.id}"), null)
    }

    private fun info(context: Context, tool: Tool, id: String): ShortcutInfoCompat {
        val intent = Intent(Intent.ACTION_MAIN).setClassName(context, LauncherEntries.activityClassName(tool.id))
        return ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(context.getString(tool.nameRes))
            .setIcon(IconCompat.createWithResource(context, icons.getValue(tool.id)))
            .setIntent(intent)
            .build()
    }
}
