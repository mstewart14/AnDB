package ca.stewartclan.andb.ui.list

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import ca.stewartclan.andb.AnDbApplication
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.ColumnWidth
import ca.stewartclan.andb.data.DatabaseRepository
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.ListView
import ca.stewartclan.andb.data.SortKey
import ca.stewartclan.andb.data.ViewColumn
import ca.stewartclan.andb.data.ViewRepository
import ca.stewartclan.andb.data.canBeColumn
import ca.stewartclan.andb.data.canSort
import ca.stewartclan.andb.ui.common.NameProblem
import ca.stewartclan.andb.ui.common.NameValidator
import ca.stewartclan.andb.ui.common.ReportingViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

/** Navigation route for creating ([NEW_VIEW_ID]) or editing a list view. */
@Serializable
data class ViewEditorRoute(val databaseId: String, val viewId: Long)

/** The view as edited, before saving. */
data class ViewDraft(
    val id: Long,
    val name: String,
    val showAll: Boolean,
    val columns: List<ViewColumn>,
    val sort: List<SortKey>
) {
    fun toView() = ListView(id, 0, name.trim(), if (showAll) emptyList() else columns, sort)
}

/** This list with the item at [from] moved to [to]; unchanged if [to] is out of range. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> =
    if (from == to || to !in indices) this else toMutableList().apply { add(to, removeAt(from)) }

data class ViewEditorState(
    val fields: List<FieldDef>,
    val otherViewNames: List<String>,
    val draft: ViewDraft,
    val saved: Boolean = false
) {
    val columnFields: List<FieldDef> get() = fields.filter { it.canBeColumn }
    val sortFields: List<FieldDef> get() = fields.filter { it.canSort }
    val nameProblem: NameProblem get() = NameValidator.check(draft.name, otherViewNames)
}

class ViewEditorViewModel(
    private val repository: DatabaseRepository,
    private val viewStore: ViewRepository,
    private val newViewName: (Int) -> String,
    private val databaseId: String,
    private val viewId: Long
) : ReportingViewModel() {
    private val _state = MutableStateFlow<ViewEditorState?>(null)

    /** `null` while loading. */
    val state: StateFlow<ViewEditorState?> = _state.asStateFlow()

    init {
        launchReporting {
            val fields = repository.fields(databaseId)
            val views = viewStore.views(databaseId)
            val view = views.find { it.id == viewId }
            val draft = if (view != null) {
                ViewDraft(view.id, view.name, view.showsAllFields, view.columns, view.sort)
            } else {
                // A new view starts from the columns all fields would show, ready to trim.
                val columns = fields.filter { it.canBeColumn }.map { ViewColumn(it.id) }
                ViewDraft(NEW_VIEW_ID, newViewName(views.size + 1), showAll = false, columns, emptyList())
            }
            _state.value = ViewEditorState(fields, views.filter { it.id != viewId }.map { it.name }, draft)
        }
    }

    fun edit(change: (ViewDraft) -> ViewDraft) = _state.update { it?.copy(draft = change(it.draft)) }

    fun addColumn(field: FieldDef) = edit { it.copy(columns = it.columns + ViewColumn(field.id)) }

    fun setWidth(fieldId: Long, width: ColumnWidth) = edit { draft ->
        draft.copy(columns = draft.columns.map { if (it.fieldId == fieldId) it.copy(width = width) else it })
    }

    fun removeColumn(fieldId: Long) = edit { draft ->
        draft.copy(columns = draft.columns.filter { it.fieldId != fieldId })
    }

    /** Adds a sort level on the first sortable field not already sorted by. */
    fun addSortLevel() = edit { draft ->
        val used = draft.sort.map { it.fieldId }.toSet()
        val field = _state.value?.sortFields?.firstOrNull { it.id !in used } ?: return@edit draft
        draft.copy(sort = draft.sort + SortKey(field.id))
    }

    fun setSortLevel(index: Int, key: SortKey) = edit { draft ->
        draft.copy(sort = draft.sort.mapIndexed { i, old -> if (i == index) key else old })
    }

    fun removeSortLevel(index: Int) = edit { draft ->
        draft.copy(sort = draft.sort.filterIndexed { i, _ -> i != index })
    }

    /** Saves the view and makes it the one shown; then [ViewEditorState.saved] is set. */
    fun save() = launchReporting {
        val state = _state.value?.takeIf { it.nameProblem == NameProblem.OK } ?: return@launchReporting
        val id = viewStore.save(databaseId, state.draft.toView())
        viewStore.setCurrentView(databaseId, id)
        _state.value = state.copy(saved = true)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AnDbApplication
                val route = createSavedStateHandle().toRoute<ViewEditorRoute>()
                ViewEditorViewModel(
                    app.repository,
                    app.views,
                    { n -> app.getString(R.string.view_numbered_name, n) },
                    route.databaseId,
                    route.viewId
                )
            }
        }
    }
}
