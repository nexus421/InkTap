package bayern.kickner.appothek

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import bayern.kickner.appothek.core.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// The only DataStore on "appothek" in the process. "settings" belongs to the Buchstabierer, see LegacyStorage.
private val Context.appothekStore: DataStore<Preferences> by preferencesDataStore(name = "appothek")

/** Settings of the Appothek itself. */
class AppSettings(private val dataStore: DataStore<Preferences>) {

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME] = mode.name }
    }

    /** Whether the one-time "Buchstabierer moved into the Appothek" question was handled. */
    val migrationHintDone: Flow<Boolean> = dataStore.data.map { it[MIGRATION_HINT_DONE] ?: false }

    suspend fun setMigrationHintDone() {
        dataStore.edit { it[MIGRATION_HINT_DONE] = true }
    }

    companion object {
        private val THEME = stringPreferencesKey("theme_mode")
        private val MIGRATION_HINT_DONE = booleanPreferencesKey("migration_hint_done")

        fun from(context: Context) = AppSettings(context.applicationContext.appothekStore)
    }
}
