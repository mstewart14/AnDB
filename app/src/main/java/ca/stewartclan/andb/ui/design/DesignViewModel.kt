package ca.stewartclan.andb.ui.design

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import ca.stewartclan.andb.AnDbApplication
import ca.stewartclan.andb.data.DatabaseRepository
import ca.stewartclan.andb.data.DatabaseSummary
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.FieldProps
import ca.stewartclan.andb.data.FieldType
import ca.stewartclan.andb.ui.common.NameProblem
import ca.stewartclan.andb.ui.common.NameValidator
import ca.stewartclan.andb.ui.common.ReportingViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class DesignViewModel(private val repository: DatabaseRepository, val databaseId: String) : ReportingViewModel() {

    val database: StateFlow<DatabaseSummary?> = repository.databases
        .map { dbs -> dbs?.find { it.id == databaseId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** All databases, for choosing a List's lookup source. */
    val databases: StateFlow<List<DatabaseSummary>?> = repository.databases

    private val _lookupInfo = MutableStateFlow(LookupInfo())

    /** Fields of the lookup database being chosen, and values of the chosen field. */
    val lookupInfo: StateFlow<LookupInfo> = _lookupInfo.asStateFlow()

    private val _fields = MutableStateFlow<List<FieldDef>?>(null)

    /** Fields in display order; `null` until loaded. */
    val fields: StateFlow<List<FieldDef>?> = _fields.asStateFlow()

    init {
        launchReporting {
            if (repository.databases.value == null) repository.refresh()
            reload()
        }
    }

    fun checkName(name: String, editing: FieldDef? = null): NameProblem =
        NameValidator.check(name, fields.value.orEmpty().map { it.name }, editing?.name)

    fun addField(name: String, type: FieldType, props: FieldProps) = change {
        repository.addField(databaseId, name.trim(), type, props)
    }

    fun updateField(field: FieldDef, name: String, type: FieldType, props: FieldProps) = change {
        if (name.trim() != field.name || type != field.type || props != field.props) {
            repository.updateField(databaseId, field.id, name.trim(), type, props)
        }
    }

    /** Moves [field] [by] places in display order (negative is up). */
    fun moveField(field: FieldDef, by: Int) = change {
        val index = fields.value.orEmpty().indexOfFirst { it.id == field.id }
        repository.moveField(databaseId, field.id, index + by)
    }

    /** Moves [field] to position [index], as dropped by drag and drop. */
    fun moveFieldTo(field: FieldDef, index: Int) = change {
        repository.moveField(databaseId, field.id, index)
    }

    /** Loads what the List editor shows for a lookup of [fieldId] in [lookupDatabaseId]. */
    fun loadLookup(lookupDatabaseId: String?, fieldId: Long?) {
        launchReporting {
            _lookupInfo.value = LookupInfo()
            if (lookupDatabaseId == null) return@launchReporting
            val fields = repository.fields(lookupDatabaseId)
            _lookupInfo.value = LookupInfo(fields)
            if (fieldId != null) {
                _lookupInfo.value = LookupInfo(fields, repository.distinctValues(lookupDatabaseId, fieldId))
            }
        }
    }

    fun deleteField(field: FieldDef) = change {
        repository.deleteField(databaseId, field.id)
    }

    private suspend fun reload() {
        _fields.value = repository.fields(databaseId)
    }

    /** Applies a change, then reloads the fields whether or not it succeeded. */
    private fun change(block: suspend () -> Unit) {
        launchReporting {
            try {
                block()
            } finally {
                reload()
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                DesignViewModel(
                    (this[APPLICATION_KEY] as AnDbApplication).repository,
                    createSavedStateHandle().toRoute<DesignRoute>().databaseId
                )
            }
        }
    }
}
