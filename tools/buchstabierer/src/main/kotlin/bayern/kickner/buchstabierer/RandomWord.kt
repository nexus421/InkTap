package bayern.kickner.buchstabierer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Reader

/** Random nouns per letter from `res/raw/words.txt` (Free German Dictionary, see wordlist/SOURCE.md). */
object RandomWord {

    @Volatile
    private var words: Map<Char, List<String>> = emptyMap()

    /** Loads the list once per process. */
    suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        if (words.isNotEmpty()) return@withContext
        words = context.resources.openRawResource(R.raw.words).bufferedReader().use(::parse)
    }

    fun parse(reader: Reader): Map<Char, List<String>> =
        reader.readLines().filter { it.isNotBlank() }.groupBy { it.first().lowercaseChar() }

    fun forLetter(letter: Char): String = words[letter.lowercaseChar()]?.randomOrNull() ?: letter.toString()
}
