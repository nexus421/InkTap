package bayern.kickner.buchstabierer

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Data written by Buchstabierer 7.0 must read back unchanged after the update to the Appothek.
 * The keys below are written raw, exactly as version 7.0 stored them.
 */
class LegacyStorageTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `file names stay those of version 7_0`() {
        assertEquals("settings", LegacyStorage.DATASTORE_NAME)
        assertEquals("settings", LegacyStorage.PREFS_NAME)
    }

    @Test
    fun `stored type names stay those of version 7_0`() {
        assertEquals(
            listOf("DIN5006", "DIN5006_2022", "RANDOM_WORD", "ROBERT_LOGGIA", "INTERNATIONAL", "CUSTOM"),
            Type.entries.map { it.name },
        )
    }

    @Test
    fun `values written by version 7_0 are read back`() = runBlocking {
        val dataStore = PreferenceDataStoreFactory.create { folder.root.resolve("settings.preferences_pb") }
        dataStore.edit {
            it[stringPreferencesKey("selected_type")] = "CUSTOM"
            it[stringPreferencesKey("name")] = "Max"
            it[booleanPreferencesKey("show_debug")] = true
            it[intPreferencesKey("favorite_buttons_row_count")] = 4
            it[stringPreferencesKey("history")] = "Haus|Baum"
            it[stringPreferencesKey("custom_alphabet")] = "A:Apfel|B:Birne"
        }
        val repository = SettingsRepository(dataStore)

        assertEquals(Type.CUSTOM, repository.selectedTypeFlow.first())
        assertEquals("Max", repository.nameFlow.first())
        assertEquals(true, repository.showDebugFlow.first())
        assertEquals(4, repository.favoriteButtonsRowCountFlow.first())
        assertEquals(listOf("Haus", "Baum"), repository.historyFlow.first())
        assertEquals(mapOf("A" to "Apfel", "B" to "Birne"), repository.customAlphabetFlow.first())
    }

    @Test
    fun `values written by the port use the same keys`() = runBlocking {
        val dataStore = PreferenceDataStoreFactory.create { folder.root.resolve("settings.preferences_pb") }
        val repository = SettingsRepository(dataStore)
        repository.setSelectedType(Type.INTERNATIONAL)
        repository.addToHistory("Haus")
        repository.updateCustomAlphabet("A", "Apfel")

        val raw = dataStore.data.first()
        assertEquals("INTERNATIONAL", raw[stringPreferencesKey("selected_type")])
        assertEquals("Haus", raw[stringPreferencesKey("history")])
        assertEquals("A:Apfel", raw[stringPreferencesKey("custom_alphabet")])
    }
}
