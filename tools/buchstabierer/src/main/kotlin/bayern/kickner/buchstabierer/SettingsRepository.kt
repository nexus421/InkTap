package bayern.kickner.buchstabierer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// The only DataStore on this file in the whole process, see LegacyStorage.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = LegacyStorage.DATASTORE_NAME)

/** The settings of the Buchstabierer. Keys and value formats are those of version 7.0, never change them. */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    companion object {
        fun from(context: Context) = SettingsRepository(context.applicationContext.dataStore)
    }

    private object PreferencesKeys {
        val SELECTED_TYPE = stringPreferencesKey("selected_type")
        val NAME = stringPreferencesKey("name")
        val SHOW_DEBUG = booleanPreferencesKey("show_debug")
        val FAVORITE_BUTTONS_ROW_COUNT = intPreferencesKey("favorite_buttons_row_count")
        val HISTORY = stringPreferencesKey("history")
        val CUSTOM_ALPHABET = stringPreferencesKey("custom_alphabet")
    }

    val selectedTypeFlow: Flow<Type> = dataStore.data.map { preferences ->
        val typeName = preferences[PreferencesKeys.SELECTED_TYPE] ?: Type.DIN5006.name
        try {
            Type.valueOf(typeName)
        } catch (e: Exception) {
            Type.DIN5006
        }
    }

    val nameFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.NAME] ?: ""
    }

    val showDebugFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SHOW_DEBUG] ?: false
    }

    val favoriteButtonsRowCountFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.FAVORITE_BUTTONS_ROW_COUNT] ?: 2
    }

    val historyFlow: Flow<List<String>> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.HISTORY]?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
    }

    val customAlphabetFlow: Flow<Map<String, String>> = dataStore.data.map { preferences ->
        val json = preferences[PreferencesKeys.CUSTOM_ALPHABET] ?: ""
        // Format since version 7.0: A:Word|B:Word|...
        json.split("|")
            .filter { it.contains(":") }
            .associate {
                val parts = it.split(":")
                parts[0] to parts[1]
            }
    }

    suspend fun setSelectedType(type: Type) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_TYPE] = type.name
        }
    }

    suspend fun setName(name: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.NAME] = name
        }
    }

    suspend fun setShowDebug(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_DEBUG] = show
        }
    }

    suspend fun setFavoriteButtonsRowCount(count: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FAVORITE_BUTTONS_ROW_COUNT] = count
        }
    }

    suspend fun addToHistory(word: String) {
        if (word.isBlank() || word.length < 2) return
        dataStore.edit { preferences ->
            val currentHistory = preferences[PreferencesKeys.HISTORY]?.split("|")?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
            currentHistory.remove(word)
            currentHistory.add(0, word)
            val newHistory = currentHistory.take(10).joinToString("|")
            preferences[PreferencesKeys.HISTORY] = newHistory
        }
    }

    suspend fun updateCustomAlphabet(letter: String, word: String) {
        dataStore.edit { preferences ->
            val currentJson = preferences[PreferencesKeys.CUSTOM_ALPHABET] ?: ""
            val currentMap = currentJson.split("|")
                .filter { it.contains(":") }
                .associate {
                    val parts = it.split(":")
                    parts[0] to parts[1]
                }.toMutableMap()
            
            currentMap[letter] = word
            val newJson = currentMap.entries.joinToString("|") { "${it.key}:${it.value}" }
            preferences[PreferencesKeys.CUSTOM_ALPHABET] = newJson
        }
    }
}