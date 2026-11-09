package bayern.kickner.appothek

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import bayern.kickner.appothek.core.Tool
import bayern.kickner.appothek.core.ToolAvailability

/** The launcher aliases that let a tool appear as its own app. Class names must match the manifest. */
object LauncherEntries {

    private val classNames = mapOf("buchstabierer" to "Buchstabierer", "inktap" to "InkTap")

    fun aliasClassName(toolId: String): String = "bayern.kickner.appothek.launcher." + classNames.getValue(toolId)

    fun activityClassName(toolId: String): String = "bayern.kickner.appothek.tool." + classNames.getValue(toolId) + "Activity"

    fun isEnabled(context: Context, toolId: String): Boolean =
        context.packageManager.getComponentEnabledSetting(alias(context, toolId)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    /** Shows or hides the tool's own launcher icon. Returns false and changes nothing if the tool cannot run here. */
    fun setEnabled(context: Context, tool: Tool, enabled: Boolean): Boolean {
        if (enabled && tool.availability(context) != ToolAvailability.Available) return false
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        context.packageManager.setComponentEnabledSetting(alias(context, tool.id), state, PackageManager.DONT_KILL_APP)
        return true
    }

    /** Hides icons of tools that cannot run on this device, e.g. after a backup was restored to a phone without NFC. */
    fun sync(context: Context, tools: List<Tool>) {
        tools.filter { it.availability(context) != ToolAvailability.Available && isEnabled(context, it.id) }
            .forEach { setEnabled(context, it, false) }
    }

    private fun alias(context: Context, toolId: String) = ComponentName(context.packageName, aliasClassName(toolId))
}
