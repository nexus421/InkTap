package bayern.kickner.buchstabierer

import kotlin.test.Test
import kotlin.test.assertEquals

class RandomWordTest {
    @Test
    fun `words are grouped by lowercase first letter`() {
        val words = RandomWord.parse("Apfel\nAst\nBaum\n\nÄrger\n".reader())
        assertEquals(listOf("Apfel", "Ast"), words['a'])
        assertEquals(listOf("Baum"), words['b'])
        assertEquals(listOf("Ärger"), words['ä'])
        assertEquals(3, words.size)
    }
}
