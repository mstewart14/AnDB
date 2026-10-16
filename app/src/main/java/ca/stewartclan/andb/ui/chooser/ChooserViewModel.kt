package ca.stewartclan.andb.ui.chooser

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.stewartclan.andb.AnDbApplication
import ca.stewartclan.andb.data.DatabaseRepository
import ca.stewartclan.andb.data.DatabaseSummary
import ca.stewartclan.andb.data.DatabaseTransfer
import ca.stewartclan.andb.data.create
import ca.stewartclan.andb.ui.common.NameProblem
import ca.stewartclan.andb.ui.common.NameValidator
import ca.stewartclan.andb.ui.common.ReportingViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Something done to a database file, for the chooser to say. */
sealed interface TransferNotice {
    data class Exported(val name: String) : TransferNotice

    data class Imported(val name: String) : TransferNotice
}

class ChooserViewModel(private val repository: DatabaseRepository, private val transfer: DatabaseTransfer) :
    ReportingViewModel() {
    /** `null` while the first load is in progress. */
    val databases: StateFlow<List<DatabaseSummary>?> = repository.databases

    init {
        launchReporting { repository.refresh() }
    }

    fun checkName(name: String, renaming: DatabaseSummary? = null): NameProblem =
        NameValidator.check(name, databases.value.orEmpty().map { it.name }, renaming?.name)

    /**
     * Creates a database, blank or from [start], then calls [onCreated] with its id so the caller
     * can open its design.
     */
    fun create(name: String, start: StartFrom, onCreated: (String) -> Unit) {
        launchReporting {
            val trimmed = name.trim()
            val id = when (start) {
                StartFrom.Blank -> repository.create(trimmed)
                is StartFrom.FromTemplate -> repository.create(trimmed, start.template)
                is StartFrom.DesignOf -> repository.duplicate(start.databaseId, trimmed, withRecords = false)
            }
            onCreated(id)
        }
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

    private val _notice = MutableStateFlow<TransferNotice?>(null)

    /** The last export or import, until the screen has said so. */
    val notice: StateFlow<TransferNotice?> = _notice.asStateFlow()

    fun noticeShown() {
        _notice.value = null
    }

    /** Writes [database] as an `.andb` file through [write]: its design, and its records if [withRecords]. */
    fun export(database: DatabaseSummary, withRecords: Boolean, write: suspend (ByteArray) -> Unit) {
        launchReporting {
            write(transfer.export(database.id, withRecords))
            _notice.value = TransferNotice.Exported(database.name)
        }
    }

    /**
     * Hands [database] as an `.andb` file's bytes to [send], to share: its design, and its records
     * if [withRecords].
     */
    fun share(database: DatabaseSummary, withRecords: Boolean, send: suspend (ByteArray) -> Unit) {
        launchReporting { send(transfer.export(database.id, withRecords)) }
    }

    /** Adds the database in the `.andb` file [read] returns. */
    fun import(read: suspend () -> ByteArray) {
        launchReporting {
            val id = transfer.import(read())
            _notice.value = TransferNotice.Imported(databases.value.orEmpty().find { it.id == id }?.name.orEmpty())
        }
    }

    fun delete(database: DatabaseSummary) {
        launchReporting { repository.delete(database.id) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val repository = (this[APPLICATION_KEY] as AnDbApplication).repository
                ChooserViewModel(repository, DatabaseTransfer(repository))
            }
        }
    }
}
