package ca.stewartclan.andb.ui.chooser

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.stewartclan.andb.AnDbApplication
import ca.stewartclan.andb.data.DatabaseRepository
import ca.stewartclan.andb.data.DatabaseSummary
import ca.stewartclan.andb.ui.common.NameProblem
import ca.stewartclan.andb.ui.common.NameValidator
import ca.stewartclan.andb.ui.common.ReportingViewModel
import kotlinx.coroutines.flow.StateFlow

class ChooserViewModel(private val repository: DatabaseRepository) : ReportingViewModel() {
    /** `null` while the first load is in progress. */
    val databases: StateFlow<List<DatabaseSummary>?> = repository.databases

    init {
        launchReporting { repository.refresh() }
    }

    fun checkName(name: String, renaming: DatabaseSummary? = null): NameProblem =
        NameValidator.check(name, databases.value.orEmpty().map { it.name }, renaming?.name)

    /** Creates a database, then calls [onCreated] with its id so the caller can open its design. */
    fun create(name: String, onCreated: (String) -> Unit) {
        launchReporting { onCreated(repository.create(name.trim())) }
    }

    fun rename(database: DatabaseSummary, name: String) {
        launchReporting { repository.rename(database.id, name.trim()) }
    }

    /** Copies [database] as [name]: its design, and its records if [withRecords]. */
    fun duplicate(database: DatabaseSummary, name: String, withRecords: Boolean) {
        launchReporting { repository.duplicate(database.id, name.trim(), withRecords) }
    }

    /** "<name> copy", or "<name> copy 2"… if that name is taken. */
    fun copyName(database: DatabaseSummary, copy: (String) -> String): String =
        (sequenceOf(copy(database.name)) + generateSequence(2) { it + 1 }.map { "${copy(database.name)} $it" })
            .first { checkName(it) == NameProblem.OK }

    fun delete(database: DatabaseSummary) {
        launchReporting { repository.delete(database.id) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { ChooserViewModel((this[APPLICATION_KEY] as AnDbApplication).repository) }
        }
    }
}
