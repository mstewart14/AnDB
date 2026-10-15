package ca.stewartclan.andb.ui.list

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import ca.stewartclan.andb.AnDbApplication
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.Condition
import ca.stewartclan.andb.data.DatabaseRepository
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.FieldType
import ca.stewartclan.andb.data.Filter
import ca.stewartclan.andb.data.FilterRepository
import ca.stewartclan.andb.data.RecordRepository
import ca.stewartclan.andb.data.canFilter
import ca.stewartclan.andb.data.valueKind
import ca.stewartclan.andb.ui.common.NameProblem
import ca.stewartclan.andb.ui.common.NameValidator
import ca.stewartclan.andb.ui.common.ReportingViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

/** Navigation route for creating ([NEW_FILTER_ID]) or editing a saved filter. */
@Serializable
data class FilterEditorRoute(val databaseId: String, val filterId: Long)

/** Filter id meaning "a new filter". Stored filters' ids start at 1. */
const val NEW_FILTER_ID = 0L

data class FilterEditorState(
    /** Fields a condition can test. */
    val fields: List<FieldDef>,
    val otherFilterNames: List<String>,
    val draft: Filter,
    /** What each List field offers, by field id, for picking values. */
    val listChoices: Map<Long, List<String>>,
    /** Set once saved or deleted, to leave the editor. */
    val done: Boolean = false
) {
    val nameProblem: NameProblem get() = NameValidator.check(draft.name, otherFilterNames)

    val fieldsById: Map<Long, FieldDef> get() = fields.associateBy { it.id }

    /** A name, at least one condition, and every condition complete. */
    val canSave: Boolean
        get() = nameProblem == NameProblem.OK &&
            draft.conditions.isNotEmpty() &&
            draft.conditions.all { c -> fieldsById[c.fieldId]?.let { FilterMatcher.isComplete(c, it) } == true }
}

class FilterEditorViewModel(
    private val repository: DatabaseRepository,
    private val records: RecordRepository,
    private val filterStore: FilterRepository,
    private val newFilterName: (Int) -> String,
    private val databaseId: String,
    private val filterId: Long
) : ReportingViewModel() {
    private val _state = MutableStateFlow<FilterEditorState?>(null)

    /** `null` while loading. */
    val state: StateFlow<FilterEditorState?> = _state.asStateFlow()

    init {
        launchReporting {
            val fields = repository.fields(databaseId).filter { it.canFilter }
            val filters = filterStore.filters(databaseId)
            val draft = filters.find { it.id == filterId }
                ?: Filter(NEW_FILTER_ID, 0, newFilterName(filters.size + 1), matchAll = true, emptyList())
            val listChoices = fields.filter { it.type == FieldType.LIST }.associate { it.id to records.choicesFor(it) }
            _state.value = FilterEditorState(
                fields,
                filters.filter { it.id != filterId }.map { it.name },
                draft,
                listChoices
            )
        }
    }

    fun edit(change: (Filter) -> Filter) = _state.update { it?.copy(draft = change(it.draft)) }

    /** Adds a condition on the first field, with that field's first operator. */
    fun addCondition() {
        val field = _state.value?.fields?.firstOrNull() ?: return
        edit { it.copy(conditions = it.conditions + Condition(field.id, field.valueKind.ops.first())) }
    }

    /**
     * Replaces condition [index] with [condition]. Choosing another field starts the condition over
     * with that field's first operator, as operators and values depend on the field's type.
     */
    fun setCondition(index: Int, condition: Condition) = edit { draft ->
        val old = draft.conditions[index]
        val field = _state.value?.fieldsById?.get(condition.fieldId)
        val fixed = if (field != null && condition.fieldId != old.fieldId) {
            Condition(field.id, field.valueKind.ops.first())
        } else {
            condition
        }
        draft.copy(conditions = draft.conditions.mapIndexed { i, c -> if (i == index) fixed else c })
    }

    fun removeCondition(index: Int) = edit { draft ->
        draft.copy(conditions = draft.conditions.filterIndexed { i, _ -> i != index })
    }

    /** Saves the filter and applies it to the list. */
    fun save() = launchReporting {
        val state = _state.value?.takeIf { it.canSave } ?: return@launchReporting
        val id = filterStore.save(databaseId, state.draft.copy(name = state.draft.name.trim()))
        filterStore.setCurrentFilter(databaseId, id)
        _state.value = state.copy(done = true)
    }

    fun delete() = launchReporting {
        val state = _state.value ?: return@launchReporting
        if (state.draft.id != NEW_FILTER_ID) filterStore.delete(databaseId, state.draft.id)
        _state.value = state.copy(done = true)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AnDbApplication
                val route = createSavedStateHandle().toRoute<FilterEditorRoute>()
                FilterEditorViewModel(
                    app.repository,
                    app.records,
                    app.filters,
                    { n -> app.getString(R.string.filter_numbered_name, n) },
                    route.databaseId,
                    route.filterId
                )
            }
        }
    }
}
