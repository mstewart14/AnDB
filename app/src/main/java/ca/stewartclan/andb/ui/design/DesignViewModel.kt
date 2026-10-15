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

class DesignViewModel(private val repository: DatabaseRepository, private val databaseId: String) :
    ReportingViewModel() {

    val database: StateFlow<DatabaseSummary?> = repository.databases
        .map { dbs -> dbs?.find { it.id == databaseId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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

    fun addField(name: String, type: FieldType) = change {
        repository.addField(databaseId, name.trim(), type)
    }

    fun updateField(field: FieldDef, name: String, type: FieldType) = change {
        if (name.trim() != field.name) repository.renameField(databaseId, field.id, name.trim())
        if (type != field.type) repository.changeFieldType(databaseId, field.id, type)
    }

    /** Moves [field] [by] places in display order (negative is up). */
    fun moveField(field: FieldDef, by: Int) = change {
        val index = fields.value.orEmpty().indexOfFirst { it.id == field.id }
        repository.moveField(databaseId, field.id, index + by)
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
