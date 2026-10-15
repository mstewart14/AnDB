package ca.stewartclan.andb.ui.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import ca.stewartclan.andb.AnDbApplication
import ca.stewartclan.andb.data.ColumnWidth
import ca.stewartclan.andb.data.DatabaseRepository
import ca.stewartclan.andb.data.DatabaseSummary
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.FieldType
import ca.stewartclan.andb.data.Filter
import ca.stewartclan.andb.data.FilterRepository
import ca.stewartclan.andb.data.LinkValue
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
import kotlinx.serialization.json.Json

/** A column of the list: [field] shown at [width]. */
data class ListColumn(val field: FieldDef, val width: ColumnWidth)

/** What a list cell shows: text, and a thumbnail for photo links. */
data class ListCell(val text: String, val thumbnail: ByteArray? = null)

/** A record as a list row: one cell per column. */
data class RecordRow(val id: Long, val cells: List<ListCell>)

data class ListUiState(
    val views: List<ListView>,
    val view: ListView,
    val columns: List<ListColumn>,
    /** The rows shown: all records, or those passing [filter] and matching [find]. */
    val rows: List<RecordRow>,
    val totalCount: Int,
    val hasFields: Boolean,
    /** Fields Find can search, for choosing one. */
    val searchable: List<FieldDef>,
    /** The search in progress, or null when not finding. */
    val find: FindQuery?,
    val filters: List<Filter>,
    /** The filter applied, or null for all records. */
    val filter: Filter?
)

/** Everything loaded for the current view, before the filter and Find narrow it. */
private class Loaded(
    val views: List<ListView>,
    val view: ListView,
    val columns: List<ListColumn>,
    val fields: List<FieldDef>,
    val entries: List<Entry>,
    val filters: List<Filter>,
    val filter: Filter?
) {
    val searchable: List<FieldDef> get() = fields.filter { it.isSearchable }
}

/** A record with its list row and what each searchable field shows, for Find. */
private class Entry(val record: Record, val row: RecordRow, val texts: Map<Long, String>)

private val FieldDef.isSearchable: Boolean get() = canBeColumn && type != FieldType.BOOLEAN

class RecordListViewModel(
    private val repository: DatabaseRepository,
    private val records: RecordRepository,
    private val viewStore: ViewRepository,
    private val filterStore: FilterRepository,
    private val browseOrder: BrowseOrder,
    private val savedState: SavedStateHandle,
    val databaseId: String
) : ReportingViewModel() {

    val database: StateFlow<DatabaseSummary?> = repository.databases
        .map { dbs -> dbs?.find { it.id == databaseId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _state = MutableStateFlow<ListUiState?>(null)

    /** `null` while loading. */
    val state: StateFlow<ListUiState?> = _state.asStateFlow()

    private var loaded: Loaded? = null

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

    /** Applies [filter] to the list, or none; remembered for the database. */
    fun setFilter(filter: Filter?) = launchReporting {
        filterStore.setCurrentFilter(databaseId, filter?.id)
        load(_state.value?.view?.id)
    }

    /** Starts finding, or changes what to find; null stops and shows all records again. */
    fun setFind(query: FindQuery?) {
        savedState[KEY_FIND] = query?.let { Json.encodeToString(FindQuery.serializer(), it) }
        publish()
    }

    private val find: FindQuery?
        get() = savedState.get<String>(KEY_FIND)?.let { Json.decodeFromString(FindQuery.serializer(), it) }

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
        val views = viewStore.views(databaseId)
        val wanted = viewId ?: viewStore.currentViewId(databaseId)
        val view = views.find { it.id == wanted } ?: views.first()
        val columns = columnsOf(view, fields)
        val filters = filterStore.filters(databaseId)
        val filterId = filterStore.currentFilterId(databaseId)
        val searchable = fields.filter { it.isSearchable }
        val entries = records.records(databaseId, view.sort).map { record ->
            Entry(
                record,
                RecordRow(
                    record.id,
                    columns.map {
                        ListCell(shownText(it.field, record), (record.values[it.field.id] as? LinkValue)?.thumbnail)
                    }
                ),
                searchable.associate { it.id to shownText(it, record) }
            )
        }
        loaded = Loaded(views, view, columns, fields, entries, filters, filters.find { it.id == filterId })
        publish()
    }

    /** Shows the loaded records narrowed by Find; the record view then browses the same ones. */
    private fun publish() {
        val data = loaded ?: return
        val query = find
        val matches = query?.matcher() ?: { true }
        val fieldsById = data.fields.associateBy { it.id }
        val rows = data.entries.filter { entry ->
            data.filter?.let { FilterMatcher.matches(it, fieldsById, entry.record) } != false && matches(entry.texts)
        }.map { it.row }
        browseOrder[databaseId] = rows.map { it.id }
        _state.value = ListUiState(
            views = data.views,
            view = data.view,
            columns = data.columns,
            rows = rows,
            totalCount = data.entries.size,
            hasFields = data.fields.isNotEmpty(),
            searchable = data.searchable,
            find = query,
            filters = data.filters,
            filter = data.filter
        )
    }

    private fun columnsOf(view: ListView, fields: List<FieldDef>): List<ListColumn> {
        val byId = fields.associateBy { it.id }
        return if (view.showsAllFields) {
            fields.filter { it.canBeColumn }.map { ListColumn(it, ColumnWidth.MEDIUM) }
        } else {
            view.columns.mapNotNull { column -> byId[column.fieldId]?.let { ListColumn(it, column.width) } }
        }
    }

    /** What [field] of [record] shows in the list. */
    private fun shownText(field: FieldDef, record: Record): String = when {
        field.isMultiList -> record.choices[field.id].orEmpty().joinToString(", ")
        field.type == FieldType.CREATED -> FieldValues.timestamp(record.created)
        field.type == FieldType.MODIFIED -> FieldValues.timestamp(record.modified)
        else -> FieldValues.display(field, record.values[field.id])
    }

    companion object {
        private const val KEY_FIND = "find"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AnDbApplication
                val savedState = createSavedStateHandle()
                RecordListViewModel(
                    app.repository,
                    app.records,
                    app.views,
                    app.filters,
                    app.browseOrder,
                    savedState,
                    savedState.toRoute<RecordListRoute>().databaseId
                )
            }
        }
    }
}
