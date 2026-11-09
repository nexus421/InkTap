package bayern.kickner.appothek

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The manifest entries the launcher, the update path and the "own app" behaviour depend on. */
class LauncherEntriesTest {

    private val android = "http://schemas.android.com/apk/res/android"
    private val toolIds = listOf("buchstabierer", "inktap")

    private val manifest: Element = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(File("src/main/AndroidManifest.xml"))
        .documentElement

    private fun elements(tag: String) = manifest.getElementsByTagName(tag).let { list ->
        (0 until list.length).map { list.item(it) as Element }
    }

    private fun Element.attr(name: String): String = getAttributeNS(android, name)

    private fun Element.isLauncher(): Boolean {
        val actions = getElementsByTagName("action").let { l -> (0 until l.length).map { (l.item(it) as Element).attr("name") } }
        val categories = getElementsByTagName("category").let { l -> (0 until l.length).map { (l.item(it) as Element).attr("name") } }
        return "android.intent.action.MAIN" in actions && "android.intent.category.LAUNCHER" in categories
    }

    @Test
    fun `class names come from one map`() {
        assertEquals("bayern.kickner.appothek.launcher.InkTap", LauncherEntries.aliasClassName("inktap"))
        assertEquals("bayern.kickner.appothek.tool.BuchstabiererActivity", LauncherEntries.activityClassName("buchstabierer"))
    }

    @Test
    fun `legacy buchstabierer launcher entry opens the appothek`() {
        val legacy = elements("activity-alias").single { it.attr("name") == "bayern.kickner.buchstabierer.MainActivity" }
        assertEquals("bayern.kickner.appothek.MainActivity", legacy.attr("targetActivity"))
        assertTrue(legacy.isLauncher())
        assertTrue(legacy.attr("enabled").let { it.isEmpty() || it == "true" })
    }

    @Test
    fun `every tool has its own task and a disabled launcher alias`() {
        toolIds.forEach { id ->
            val activity = elements("activity").single { it.attr("name") == LauncherEntries.activityClassName(id) }
            assertTrue(activity.attr("taskAffinity").endsWith(".tool.$id"), "taskAffinity of $id")
            // Alias, shortcut and pinned icon must all reach the same single instance.
            assertEquals("singleTask", activity.attr("launchMode"), "launchMode of $id")
            val alias = elements("activity-alias").single { it.attr("name") == LauncherEntries.aliasClassName(id) }
            assertEquals(LauncherEntries.activityClassName(id), alias.attr("targetActivity"))
            assertEquals("false", alias.attr("enabled"), "alias of $id must start hidden")
            assertTrue(alias.isLauncher())
        }
    }

    @Test
    fun `no internet and nfc is optional`() {
        val permissions = elements("uses-permission").map { it.attr("name") }
        assertTrue("android.permission.INTERNET" !in permissions)
        val nfc = elements("uses-feature").single { it.attr("name") == "android.hardware.nfc" }
        assertEquals("false", nfc.attr("required"))
    }

    @Test
    fun `only the legacy alias and the tool aliases are launcher entries`() {
        val launchers = (elements("activity") + elements("activity-alias")).filter { it.isLauncher() }.map { it.attr("name") }
        assertEquals(
            setOf("bayern.kickner.buchstabierer.MainActivity") + toolIds.map(LauncherEntries::aliasClassName),
            launchers.toSet(),
        )
        assertNotNull(launchers)
    }
}
