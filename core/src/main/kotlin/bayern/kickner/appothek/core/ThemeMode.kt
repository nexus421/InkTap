package bayern.kickner.appothek.core

/** The user's design choice. Stored by name, so never rename the entries. */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}
