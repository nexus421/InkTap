package bayern.kickner.buchstabierer

import kotlin.test.Test
import kotlin.test.assertEquals

class SpellingTest {

    private fun spell(type: Type, letter: String, name: String = "", custom: Map<String, String> = emptyMap()) =
        Spelling.spell(type, letter, name, custom) { "Zufall" }

    @Test
    fun `tables of version 7_0`() {
        assertEquals("Anton", spell(Type.DIN5006, "a"))
        assertEquals("Aachen", spell(Type.DIN5006_2022, "a"))
        assertEquals("X-Ray", spell(Type.INTERNATIONAL, "x"))
        assertEquals("Charlotte", spell(Type.DIN5006, "ch"))
        assertEquals("Zufall", spell(Type.RANDOM_WORD, "q"))
    }

    @Test
    fun `custom table falls back to the letter`() {
        assertEquals("Birne", spell(Type.CUSTOM, "b", custom = mapOf("B" to "Birne")))
        assertEquals("c", spell(Type.CUSTOM, "c", custom = mapOf("B" to "Birne")))
    }

    @Test
    fun `robert loggia uses the name`() {
        assertEquals("Robert Loggia", spell(Type.ROBERT_LOGGIA, "r", name = "Max"))
        assertEquals("Mega dieser Hans", spell(Type.ROBERT_LOGGIA, "m", name = "Hans"))
        // Same first letter as the name: version 7.0 answers with the name alone.
        assertEquals("Max", spell(Type.ROBERT_LOGGIA, "m", name = "Max"))
        assertEquals("Alter, das ist ja Max", spell(Type.ROBERT_LOGGIA, "a", name = "Max"))
    }

    @Test
    fun `robert loggia with an empty name does not crash`() {
        assertEquals("Alter, das ist ja ", spell(Type.ROBERT_LOGGIA, "a", name = ""))
    }

    @Test
    fun `din 5006 keeps sch and ch together`() {
        val prepared = Spelling.prepareInput(Type.DIN5006, "Schach")
        assertEquals(listOf("sch", "a", "ch"), prepared.map { Spelling.prepareCharacter(Type.DIN5006, it) })
    }

    @Test
    fun `other tables spell every character`() {
        val prepared = Spelling.prepareInput(Type.INTERNATIONAL, "Ab")
        assertEquals(listOf("A", "b"), prepared.map { Spelling.prepareCharacter(Type.INTERNATIONAL, it) })
    }
}
