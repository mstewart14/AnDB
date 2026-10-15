package ca.stewartclan.andb.ui.record

import androidx.lifecycle.SavedStateHandle
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
import ca.stewartclan.andb.data.Record
import ca.stewartclan.andb.data.RecordRepository
import ca.stewartclan.andb.ui.common.ReportingViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The record on screen with its unsaved edits. */
data class RecordUiState(
    val fields: List<FieldDef>,
    /** All record ids in browsing order, for previous/next. */
    val recordIds: List<Long>,
    val record: Record,
    /** Editor text of each single-value field, by field id. */
    val drafts: Map<Long, String>,
    /** Choices of each multiple-choice List field, by field id. */
    val chosen: Map<Long, List<String>>,
    /** What each List field offers, by field id. */
    val listChoices: Map<Long, List<String>>,
    val dirty: Boolean = false
) {
    val index: Int get() = recordIds.indexOf(record.id)
}

class RecordViewModel(
    private val repository: DatabaseRepository,
    private val records: RecordRepository,
    /** Saves started when leaving the screen run here, so they finish after the ViewModel is gone. */
    private val appScope: CoroutineScope,
    private val savedState: SavedStateHandle,
    private val databaseId: String,
    startRecordId: Long
) : ReportingViewModel() {

    val database: StateFlow<DatabaseSummary?> = repository.databases
        .map { dbs -> dbs?.find { it.id == databaseId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _state = MutableStateFlow<RecordUiState?>(null)

    /** `null` while loading. */
    val state: StateFlow<RecordUiState?> = _state.asStateFlow()

    /** Protected mode: fields can't be edited. Kept across rotation. */
    val locked: StateFlow<Boolean> = savedState.getStateFlow(KEY_LOCKED, false)

    private val _finished = MutableStateFlow(false)

    /** Set when there's no record left to show (the last one was deleted). */
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    init {
        launchReporting { load(savedState[KEY_RECORD] ?: startRecordId) }
    }

    fun setLocked(locked: Boolean) {
        savedState[KEY_LOCKED] = locked
    }

    fun edit(field: FieldDef, text: String) = _state.update {
        it?.copy(drafts = it.drafts + (field.id to text), dirty = true)
    }

    fun toggleChoice(field: FieldDef, choice: String) = _state.update {
        val current = it?.chosen?.get(field.id).orEmpty()
        val updated = if (choice in current) current - choice else current + choice
        it?.copy(chosen = it.chosen + (field.id to updated), dirty = true)
    }

    /** Saves, then shows the record [by] places away in browsing order. */
    fun go(by: Int) {
        val state = _state.value ?: return
        val target = state.recordIds.getOrNull(state.index + by) ?: return
        launchReporting {
            saveNow()
            load(target)
        }
    }

    fun duplicate() = launchReporting {
        saveNow()
        val shown = _state.value ?: return@launchReporting
        load(records.duplicate(databaseId, shown.record.id))
    }

    /** Deletes the shown record and shows the next one, or the previous if it was the last. */
    fun delete() = launchReporting {
        val shown = _state.value ?: return@launchReporting
        records.delete(databaseId, shown.record.id)
        val next = shown.recordIds.getOrNull(shown.index + 1) ?: shown.recordIds.getOrNull(shown.index - 1)
        if (next == null) _finished.value = true else load(next)
    }

    /** Saves unsaved edits in the background; for leaving the screen or the app. */
    fun saveInBackground() {
        val edits = takeEdits() ?: return
        appScope.launch { edits.save() }
    }

    private suspend fun saveNow() {
        takeEdits()?.save()
    }

    private class Edits(val recordId: Long, val values: Map<Long, Any?>, val chosen: Map<Long, List<String>>)

    private suspend fun Edits.save() = records.save(databaseId, recordId, values, chosen)

    /** The shown record's unsaved edits, now marked saved; null if there are none. */
    private fun takeEdits(): Edits? {
        val state = _state.value?.takeIf { it.dirty } ?: return null
        _state.value = state.copy(dirty = false)
        val values = state.fields.filter { it.id in state.drafts }
            .associate { it.id to FieldValues.fromDraft(it, state.drafts.getValue(it.id)) }
        return Edits(state.record.id, values, state.chosen)
    }

    private suspend fun load(recordId: Long) {
        val fields = repository.fields(databaseId)
        val record = records.record(databaseId, recordId)
        if (record == null) {
            _finished.value = true
            return
        }
        val listChoices = _state.value?.listChoices ?: fields.filter { it.type == FieldType.LIST }
            .associate { it.id to records.choicesFor(it) }
        _state.value = RecordUiState(
            fields = fields,
            recordIds = records.recordIds(databaseId),
            record = record,
            drafts = record.values.mapValues { (_, v) -> FieldValues.toDraft(v) },
            chosen = record.choices,
            listChoices = listChoices
        )
        savedState[KEY_RECORD] = recordId
    }

    override fun onCleared() {
        saveInBackground()
    }

    companion object {
        private const val KEY_LOCKED = "locked"
        private const val KEY_RECORD = "shownRecord"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AnDbApplication
                val savedState = createSavedStateHandle()
                val route = savedState.toRoute<RecordRoute>()
                RecordViewModel(
                    app.repository,
                    app.records,
                    app.appScope,
                    savedState,
                    route.databaseId,
                    route.recordId
                )
            }
        }
    }
}
