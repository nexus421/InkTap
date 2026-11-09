package bayern.kickner.appothek

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Resources of all modules are merged into one app. A string name used by two modules silently overrides one of
 * them (the app's "settings" replaced the Buchstabierer's German text), so every name must be unique.
 */
class ResourceNamesTest {

    private fun stringNames(module: File): Set<String> =
        module.resolve("src/main/res").listFiles { f -> f.name.startsWith("values") }.orEmpty()
            .map { it.resolve("strings.xml") }
            .filter { it.exists() }
            .flatMap { file ->
                val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
                (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("name").nodeValue }
            }
            .toSet()

    @Test
    fun `no string name is used by two modules`() {
        val root = File("..")
        val modules = listOf(File(".")) + root.resolve("tools").listFiles { f -> f.isDirectory }.orEmpty() + root.resolve("core")
        val names = modules.associate { it.canonicalFile.name to stringNames(it) }
        val clashes = names.entries.flatMap { (module, own) ->
            names.filterKeys { it > module }.flatMap { (other, theirs) -> (own intersect theirs).map { "$it: $module/$other" } }
        }
        assertEquals(emptyList(), clashes)
    }
}
