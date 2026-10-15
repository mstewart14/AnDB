package ca.stewartclan.andb.ui.chooser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.stewartclan.andb.AnDbApplication
import ca.stewartclan.andb.data.DatabaseRepository
import ca.stewartclan.andb.data.DatabaseSummary
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChooserViewModel(private val repository: DatabaseRepository) : ViewModel() {
    /** `null` while the first load is in progress. */
    val databases: StateFlow<List<DatabaseSummary>?> = repository.databases

    init {
        viewModelScope.launch { repository.refresh() }
    }

    fun checkName(name: String, renaming: DatabaseSummary? = null): NameProblem =
        DatabaseNameValidator.check(name, databases.value.orEmpty().map { it.name }, renaming?.name)

    fun create(name: String) {
        viewModelScope.launch { repository.create(name.trim()) }
    }

    fun rename(database: DatabaseSummary, name: String) {
        viewModelScope.launch { repository.rename(database.id, name.trim()) }
    }

    fun delete(database: DatabaseSummary) {
        viewModelScope.launch { repository.delete(database.id) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { ChooserViewModel((this[APPLICATION_KEY] as AnDbApplication).repository) }
        }
    }
}
