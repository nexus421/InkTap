package bayern.kickner.buchstabierer

/** Turns letters into spelling words. Pure functions, the behaviour of version 7.0. */
object Spelling {
    // DIN 5006 spells "sch" and "ch" as one unit; they are replaced by control characters before splitting.
    private const val SCH = "\u0000"
    private const val CH = "\u0001"

    fun spell(type: Type, letter: String, name: String, custom: Map<String, String>, random: (Char) -> String): String =
        when (type) {
            Type.DIN5006 -> Table.entries.find { it.short.equals(letter, true) }?.nameGer ?: letter
            Type.DIN5006_2022 -> Table.entries.find { it.short.equals(letter, true) }?.nameGer2022 ?: letter
            Type.INTERNATIONAL -> Table.entries.find { it.short.equals(letter, true) }?.international ?: letter
            Type.RANDOM_WORD -> random(letter.first())
            Type.CUSTOM -> custom[letter.uppercase()] ?: letter
            Type.ROBERT_LOGGIA -> RobertLoggia.entries.find { it.short.equals(letter, true) }?.funPart?.let {
                when {
                    name.isNotEmpty() && it.first().equals(name.first(), ignoreCase = true) -> name
                    it == RobertLoggia.R.funPart -> it
                    else -> it + name
                }
            } ?: letter
        }

    fun prepareInput(type: Type, input: String): String = when (type) {
        Type.DIN5006 -> input.lowercase().replace("sch", SCH).replace("ch", CH)
        else -> input
    }

    fun prepareCharacter(type: Type, char: Char): String = when (type) {
        Type.DIN5006 -> when (val it = char.toString()) {
            SCH -> "sch"
            CH -> "ch"
            else -> it
        }
        else -> char.toString()
    }

    fun middle(type: Type): String = when (type) {
        Type.DIN5006, Type.DIN5006_2022 -> " = "
        Type.RANDOM_WORD -> " -> "
        Type.ROBERT_LOGGIA -> " wie "
        Type.INTERNATIONAL -> " >> "
        Type.CUSTOM -> " : "
    }
}
