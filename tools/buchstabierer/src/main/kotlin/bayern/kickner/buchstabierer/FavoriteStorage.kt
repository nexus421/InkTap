package bayern.kickner.buchstabierer

import android.content.Context
import androidx.core.content.edit

/** The favourite buttons, stored under "<column> <row>" in [LegacyStorage.PREFS_NAME] since version 7.0. */
object FavoriteStorage {

    fun get(context: Context, columnIndex: Int, rowIndex: Int): String? =
        prefs(context).getString("$columnIndex $rowIndex", null)

    fun store(context: Context, columnIndex: Int, rowIndex: Int, value: String) {
        prefs(context).edit { putString("$columnIndex $rowIndex", value.ifBlank { null }) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(LegacyStorage.PREFS_NAME, Context.MODE_PRIVATE)
}
