package ca.stewartclan.andb.ui.list

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
import ca.stewartclan.andb.data.Record
import ca.stewartclan.andb.data.RecordRepository
import ca.stewartclan.andb.ui.common.ReportingViewModel
import ca.stewartclan.andb.ui.record.FieldValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A record as a list row: the values of its first few fields that have any. */
data class RecordRow(val id: Long, val values: List<String>)

class RecordListViewModel(
    private val repository: DatabaseRepository,
    private val records: RecordRepository,
    val databaseId: String
) : ReportingViewModel() {

    val database: StateFlow<DatabaseSummary?> = repository.databases
        .map { dbs -> dbs?.find { it.id == databaseId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _rows = MutableStateFlow<List<RecordRow>?>(null)

    /** `null` while loading. */
    val rows: StateFlow<List<RecordRow>?> = _rows.asStateFlow()

    /** Reloads the rows; called whenever the list is shown, since records change elsewhere. */
    fun reload() {
        launchReporting {
            if (repository.databases.value == null) repository.refresh()
            val fields = repository.fields(databaseId)
            _rows.value = records.records(databaseId).map { row(it, fields) }
        }
    }

    private fun row(record: Record, fields: List<FieldDef>) = RecordRow(
        record.id,
        fields.mapNotNull { field ->
            val text = if (field.isMultiList) {
                record.choices[field.id].orEmpty().joinToString(", ")
            } else {
                FieldValues.display(field, record.values[field.id])
            }
            text.takeIf { it.isNotBlank() }
        }.take(ROW_VALUES)
    )

    companion object {
        /** How many values a row shows until list views choose their columns. */
        private const val ROW_VALUES = 3

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AnDbApplication
                RecordListViewModel(
                    app.repository,
                    app.records,
                    createSavedStateHandle().toRoute<RecordListRoute>().databaseId
                )
            }
        }
    }
}
