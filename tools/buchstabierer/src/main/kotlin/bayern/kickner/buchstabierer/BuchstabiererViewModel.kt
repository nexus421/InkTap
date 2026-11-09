package bayern.kickner.buchstabierer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State and actions of the Buchstabierer, as in version 7.0. */
class BuchstabiererViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository.from(application)

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    val selectedType: StateFlow<Type> = repository.selectedTypeFlow.stateIn(viewModelScope, SharingStarted.Eagerly, Type.DIN5006)
    val name: StateFlow<String> = repository.nameFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val showDebug: StateFlow<Boolean> = repository.showDebugFlow.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val favoriteButtonRows: StateFlow<Int> =
        repository.favoriteButtonsRowCountFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 2)
    val history: StateFlow<List<String>> = repository.historyFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val customAlphabet: StateFlow<Map<String, String>> =
        repository.customAlphabetFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        viewModelScope.launch {
            LegacyMigration.run(application, repository)
            RandomWord.load(application)
            _loading.value = false
        }
    }

    fun incrementButtonRows() {
        val current = favoriteButtonRows.value
        if (current < 10) viewModelScope.launch { repository.setFavoriteButtonsRowCount(current + 1) }
    }

    fun decrementButtonRows() {
        val current = favoriteButtonRows.value
        if (current > 1) viewModelScope.launch { repository.setFavoriteButtonsRowCount(current - 1) }
    }

    fun setSelectedType(type: Type) = viewModelScope.launch { repository.setSelectedType(type) }
    fun setName(newName: String) = viewModelScope.launch { repository.setName(newName) }
    fun setShowDebug(show: Boolean) = viewModelScope.launch { repository.setShowDebug(show) }
    fun addToHistory(word: String) = viewModelScope.launch { repository.addToHistory(word) }
    fun updateCustomAlphabet(letter: String, word: String) =
        viewModelScope.launch { repository.updateCustomAlphabet(letter, word) }

    fun textForLetter(letter: String, type: Type = selectedType.value): String =
        Spelling.spell(type, letter, name.value, customAlphabet.value, RandomWord::forLetter)

    fun prepareInput(input: String): String = Spelling.prepareInput(selectedType.value, input)
    fun prepareCharacter(char: Char): String = Spelling.prepareCharacter(selectedType.value, char)
    fun middle(): String = Spelling.middle(selectedType.value)
}
