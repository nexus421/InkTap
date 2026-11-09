package bayern.kickner.buchstabierer

import android.content.Context
import androidx.core.content.edit

/**
 * Moves settings of versions before the DataStore switch from SharedPreferences to DataStore, exactly as
 * version 7.0 did in `Settings.init`. Runs once, the marker stays in the SharedPreferences.
 */
object LegacyMigration {
    private const val KEY_TYPE = "type"
    private const val KEY_NAME = "name"
    private const val KEY_DEBUG = "debug"
    private const val KEY_FAV_COUNT = "favoriteButtonsRowCount"
    private const val KEY_MIGRATED = "migrated_to_datastore"

    suspend fun run(context: Context, repository: SettingsRepository) {
        val sp = context.getSharedPreferences(LegacyStorage.PREFS_NAME, Context.MODE_PRIVATE)
        if (sp.getBoolean(KEY_MIGRATED, false)) return
        val typeName = sp.getString(KEY_TYPE, Type.DIN5006.name) ?: Type.DIN5006.name
        repository.setSelectedType(runCatching { Type.valueOf(typeName) }.getOrDefault(Type.DIN5006))
        repository.setName(sp.getString(KEY_NAME, "") ?: "")
        repository.setShowDebug(sp.getBoolean(KEY_DEBUG, false))
        repository.setFavoriteButtonsRowCount(sp.getInt(KEY_FAV_COUNT, 2))
        sp.edit { putBoolean(KEY_MIGRATED, true) }
    }
}
