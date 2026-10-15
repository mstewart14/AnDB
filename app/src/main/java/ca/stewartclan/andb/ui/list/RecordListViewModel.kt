package ca.stewartclan.andb.ui.list

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import ca.stewartclan.andb.AnDbApplication
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.ColumnWidth
import ca.stewartclan.andb.data.DatabaseRepository
import ca.stewartclan.andb.data.DatabaseSummary
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.FieldType
import ca.stewartclan.andb.data.ListView
import ca.stewartclan.andb.data.Record
import ca.stewartclan.andb.data.RecordRepository
import ca.stewartclan.andb.data.SortKey
import ca.stewartclan.andb.data.ViewRepository
import ca.stewartclan.andb.data.canBeColumn
import ca.stewartclan.andb.ui.common.BrowseOrder
import ca.stewartclan.andb.ui.common.ReportingViewModel
import ca.stewartclan.andb.ui.record.FieldValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A column of the list: [field] shown at [width]. */
data class ListColumn(val field: FieldDef, val width: ColumnWidth)

/** A record as a list row: one display text per column. */
data class RecordRow(val id: Long, val cells: List<String>)

data class ListUiState(
    val views: List<ListView>,
    val view: ListView,
    val columns: List<ListColumn>,
    val rows: List<RecordRow>,
    val hasFields: Boolean
)

class RecordListViewModel(
    private val repository: DatabaseRepository,
    private val records: RecordRepository,
    private val viewStore: ViewRepository,
    private val browseOrder: BrowseOrder,
    /** Name of the view every database starts with. */
    private val defaultViewName: String,
    val databaseId: String
) : ReportingViewModel() {

    val database: StateFlow<DatabaseSummary?> = repository.databases
        .map { dbs -> dbs?.find { it.id == databaseId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _state = MutableStateFlow<ListUiState?>(null)

    /** `null` while loading. */
    val state: StateFlow<ListUiState?> = _state.asStateFlow()

    /** Reloads everything; called whenever the list is shown, since records and design change elsewhere. */
    fun reload() = launchReporting { load(viewId = null) }

    fun selectView(view: ListView) = launchReporting {
        viewStore.setCurrentView(databaseId, view.id)
        load(view.id)
    }

    /**
     * Sorts by [field] alone, as tapping its column header does: ascending first, reversed if it's
     * already the only sort key. Saved in the view.
     */
    fun sortBy(field: FieldDef) = launchReporting {
        val view = _state.value?.view ?: return@launchReporting
        val current = view.sort.singleOrNull()?.takeIf { it.fieldId == field.id }
        val key = current?.copy(descending = !current.descending) ?: SortKey(field.id)
        viewStore.save(databaseId, view.copy(sort = listOf(key)))
        load(view.id)
    }

    /** Deletes the current view and shows the first remaining one. */
    fun deleteView() = launchReporting {
        val view = _state.value?.view ?: return@launchReporting
        viewStore.delete(databaseId, view.id)
        load(viewId = null)
    }

    /** Loads view [viewId], or the one last shown, or the first. */
    private suspend fun load(viewId: Long?) {
        if (repository.databases.value == null) repository.refresh()
        val fields = repository.fields(databaseId)
        val views = viewStore.views(databaseId, defaultViewName)
        val wanted = viewId ?: viewStore.currentViewId(databaseId)
        val view = views.find { it.id == wanted } ?: views.first()
        val columns = columnsOf(view, fields)
        val rows = records.records(databaseId, view.sort).map { record -> row(record, columns) }
        browseOrder[databaseId] = rows.map { it.id }
        _state.value = ListUiState(views, view, columns, rows, hasFields = fields.isNotEmpty())
    }

    private fun columnsOf(view: ListView, fields: List<FieldDef>): List<ListColumn> {
        val byId = fields.associateBy { it.id }
        return if (view.showsAllFields) {
            fields.filter { it.canBeColumn }.map { ListColumn(it, ColumnWidth.MEDIUM) }
        } else {
            view.columns.mapNotNull { column -> byId[column.fieldId]?.let { ListColumn(it, column.width) } }
        }
    }

    private fun row(record: Record, columns: List<ListColumn>) = RecordRow(
        record.id,
        columns.map { (field, _) ->
            when {
                field.isMultiList -> record.choices[field.id].orEmpty().joinToString(", ")
                field.type == FieldType.CREATED -> FieldValues.timestamp(record.created)
                field.type == FieldType.MODIFIED -> FieldValues.timestamp(record.modified)
                else -> FieldValues.display(field, record.values[field.id])
            }
        }
    )

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AnDbApplication
                RecordListViewModel(
                    app.repository,
                    app.records,
                    app.views,
                    app.browseOrder,
                    app.getString(R.string.view_default_name),
                    createSavedStateHandle().toRoute<RecordListRoute>().databaseId
                )
            }
        }
    }
}
