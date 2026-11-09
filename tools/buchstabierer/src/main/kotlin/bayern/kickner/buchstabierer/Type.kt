package bayern.kickner.buchstabierer

/** The spelling tables. Stored by name since version 7.0, never rename the entries. */
enum class Type {
    DIN5006,
    DIN5006_2022, // Neu seit 2022
    RANDOM_WORD,
    ROBERT_LOGGIA,
    INTERNATIONAL,
    CUSTOM,
}
