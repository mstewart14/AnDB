package ca.stewartclan.andb.ui.record

import android.util.Log
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
import ca.stewartclan.andb.data.LinkValue
import ca.stewartclan.andb.data.Record
import ca.stewartclan.andb.data.RecordRef
import ca.stewartclan.andb.data.RecordRepository
import ca.stewartclan.andb.data.RequiredRule
import ca.stewartclan.andb.ui.common.BrowseOrder
import ca.stewartclan.andb.ui.common.Facet
import ca.stewartclan.andb.ui.common.LinkChoices
import ca.stewartclan.andb.ui.common.RecordTitles
import ca.stewartclan.andb.ui.common.ReportingViewModel
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    /** Each Link field's link, or null for none, by field id. */
    val links: Map<Long, LinkValue?>,
    /** What each List field offers, by field id. */
    val listChoices: Map<Long, List<String>>,
    /** For each Link field to records: the titles of the records it can link to, by record id. */
    val recordTitles: Map<Long, Map<Long, String>> = emptyMap(),
    /** For each Link field to records: fields of the linked database to narrow its chooser by. */
    val linkFacets: Map<Long, List<Facet>> = emptyMap(),
    /** For each Linked field: what it shows for each record its Link field can link to. */
    val linkedValues: Map<Long, Map<Long, String>> = emptyMap(),
    /** Records in any database linking to this one. */
    val backlinks: List<BacklinkGroup> = emptyList(),
    val dirty: Boolean = false,
    /** Whether to point out empty required fields, after an attempt to move on without them. */
    val showMissing: Boolean = false
) {
    /** A record being entered, not stored until it's saved with its required fields filled. */
    val isNew: Boolean get() = record.id == NEW_RECORD_ID

    /** Position in [recordIds]; a new record comes after the last. */
    val index: Int get() = if (isNew) recordIds.size else recordIds.indexOf(record.id)

    /** The edited values as they would be stored. */
    val values: Map<Long, Any?> get() = RequiredFields.values(fields, drafts) + links

    /** Required fields still empty. */
    val missing: List<FieldDef> get() = RequiredRule.missing(fields, values, chosen)

    /** Nothing entered at all, as in a record added by mistake. */
    val isBlank: Boolean get() = RequiredFields.isBlank(fields, drafts, chosen) && links.values.all { it == null }

    /** Edits not yet stored. A new record with nothing entered has none worth keeping. */
    val hasUnsavedInput: Boolean get() = dirty && !(isNew && isBlank)

    /** Leaving now would lose edits, as they can't be saved until required fields are filled. */
    val wouldLoseEdits: Boolean get() = hasUnsavedInput && missing.isNotEmpty()
}

/** Records of database [databaseId] linking here through Link [field], as (record id, title). */
data class BacklinkGroup(
    val databaseId: String,
    val databaseName: String,
    val field: FieldDef,
    val records: List<Pair<Long, String>>
)

/** A new record's Link field [fieldId] starts linked to [uri], as when adding from a backlink. */
data class Prelink(val fieldId: Long, val uri: String)

/** Where the record view's data comes from. */
class RecordStores(val repository: DatabaseRepository, val records: RecordRepository, val titles: RecordTitles)

class RecordViewModel(
    private val stores: RecordStores,
    /** Saves started when leaving the screen run here, so they finish after the ViewModel is gone. */
    private val appScope: CoroutineScope,
    private val savedState: SavedStateHandle,
    private val browseOrder: BrowseOrder,
    val databaseId: String,
    startRecordId: Long,
    /** For the first new record shown, if it was started from a backlink. */
    private var prelink: Prelink? = null
) : ReportingViewModel() {

    private val repository = stores.repository
    private val records = stores.records

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

    /** One save at a time, so a new record's first save finishes (and gets its id) before the next. */
    private val saveLock = Mutex()

    init {
        launchReporting { load(savedState[KEY_RECORD] ?: startRecordId) }
    }

    fun setLocked(locked: Boolean) {
        savedState[KEY_LOCKED] = locked
    }

    fun edit(field: FieldDef, text: String) = _state.update {
        it?.copy(drafts = it.drafts + (field.id to text), dirty = true)
    }

    /** What the field editors need to show [state] and report edits; [readOnly] in protected mode. */
    fun editorContext(state: RecordUiState, readOnly: Boolean, onOpenRecord: (RecordRef) -> Unit) =
        EditorContext(state, readOnly, ::edit, ::toggleChoice, ::setLink, onOpenRecord)

    /** Links [field] to [link], or unlinks it when null. */
    fun setLink(field: FieldDef, link: LinkValue?) = _state.update {
        it?.copy(links = it.links + (field.id to link), dirty = true)
    }

    fun toggleChoice(field: FieldDef, choice: String) = _state.update {
        val current = it?.chosen?.get(field.id).orEmpty()
        val updated = if (choice in current) current - choice else current + choice
        it?.copy(chosen = it.chosen + (field.id to updated), dirty = true)
    }

    /**
     * Saves, then shows the record [by] places away in browsing order. If the edits can't be saved
     * yet, stays and points out the empty required fields.
     */
    fun go(by: Int) {
        val state = _state.value ?: return
        val target = state.recordIds.getOrNull(state.index + by) ?: return
        launchReporting { if (save()) load(target) else showMissing() }
    }

    /** Saves and starts a new record; if the edits can't be saved yet, points out what's missing. */
    fun newRecord() {
        val state = _state.value ?: return
        if (state.isNew && state.isBlank) return
        launchReporting { if (save()) load(NEW_RECORD_ID) else showMissing() }
    }

    /** Drops unsaved edits: back to the record as stored, or to a blank new record. */
    fun revert() = _state.update { state ->
        state?.copy(
            drafts = drafts(state.record),
            chosen = state.record.choices,
            links = links(state.fields, state.record),
            dirty = false,
            showMissing = false
        )
    }

    fun duplicate() = launchReporting {
        if (!save()) {
            showMissing()
            return@launchReporting
        }
        val shown = _state.value?.takeUnless { it.isNew } ?: return@launchReporting
        load(records.duplicate(databaseId, shown.record.id))
    }

    /**
     * Deletes the shown record and shows the next one, or the previous if it was the last. A new
     * record was never stored, so it's just dropped.
     */
    fun delete() = launchReporting {
        val shown = _state.value ?: return@launchReporting
        if (shown.isNew) {
            revert()
            _finished.value = true
            return@launchReporting
        }
        records.delete(databaseId, shown.record.id)
        val next = shown.recordIds.getOrNull(shown.index + 1) ?: shown.recordIds.getOrNull(shown.index - 1)
        if (next == null) _finished.value = true else load(next)
    }

    /**
     * Saves in the background, for leaving the screen or the app. Edits that can't be saved yet
     * stay on screen, unsaved.
     */
    fun saveInBackground() {
        appScope.launch {
            try {
                save()
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                Log.e(TAG, "Background save failed", e)
            }
        }
    }

    private fun showMissing() = _state.update { it?.copy(showMissing = true) }

    /**
     * Stores the shown record's edits: inserts a new record or updates a stored one. Returns false,
     * storing nothing, while required fields are empty. A new record with nothing entered isn't
     * stored at all.
     */
    private suspend fun save(): Boolean = saveLock.withLock {
        val state = _state.value?.takeIf { it.hasUnsavedInput } ?: return true
        if (state.missing.isNotEmpty()) return false
        val values = state.values
        val recordId = if (state.isNew) {
            records.insert(databaseId, values, state.chosen).also { savedState[KEY_RECORD] = it }
        } else {
            records.save(databaseId, state.record.id, values, state.chosen)
            state.record.id
        }
        val stored = records.record(databaseId, recordId) ?: return true
        _state.update { current ->
            current?.copy(
                record = stored,
                recordIds = if (state.isNew) current.recordIds + recordId else current.recordIds,
                // Edits made while saving are still unsaved.
                dirty = current.drafts != state.drafts || current.chosen != state.chosen || current.links != state.links
            )
        }
        true
    }

    private suspend fun load(recordId: Long) {
        val fields = repository.fields(databaseId)
        val record = if (recordId == NEW_RECORD_ID) {
            val now = Instant.now().toString()
            Record(NEW_RECORD_ID, now, now, emptyMap(), emptyMap())
        } else {
            records.record(databaseId, recordId)
        }
        if (record == null) {
            _finished.value = true
            return
        }
        val listChoices = _state.value?.listChoices ?: fields.filter { it.type == FieldType.LIST }
            .associate { it.id to records.choicesFor(it) }
        val choices = if (_state.value == null) loadChoices(fields) else null
        val recordTitles = _state.value?.recordTitles ?: choices.orEmpty().mapValues { it.value.titles }
        val linkFacets = _state.value?.linkFacets ?: choices.orEmpty().mapValues { it.value.facets }
        val linkedValues = _state.value?.linkedValues ?: stores.titles.linkedValues(fields)
        val links = links(fields, record) + prelinked(recordTitles)
        _state.value = RecordUiState(
            fields = fields,
            recordIds = browseOrder.arrange(databaseId, records.recordIds(databaseId)),
            record = record,
            drafts = drafts(record),
            chosen = record.choices,
            links = links,
            listChoices = listChoices,
            recordTitles = recordTitles,
            linkFacets = linkFacets,
            linkedValues = linkedValues,
            backlinks = if (record.id == NEW_RECORD_ID) emptyList() else loadBacklinks(record.id)
        )
        savedState[KEY_RECORD] = recordId
    }

    /**
     * The link a new record started from a backlink begins with, once. It isn't an edit: leaving
     * without entering anything stores nothing.
     */
    private fun prelinked(recordTitles: Map<Long, Map<Long, String>>): Map<Long, LinkValue> {
        val link = prelink.also { prelink = null }
        val title = link?.let { RecordRef.parse(it.uri) }?.let { recordTitles[link.fieldId]?.get(it.recordId) }
        return if (link != null &&
            title != null
        ) {
            mapOf(link.fieldId to LinkValue(link.uri, title, null))
        } else {
            emptyMap()
        }
    }

    private suspend fun loadBacklinks(recordId: Long): List<BacklinkGroup> =
        records.backlinks(databaseId, recordId).map { group ->
            val titles = stores.titles.values(group.databaseId, null)
            BacklinkGroup(
                group.databaseId,
                group.databaseName,
                group.field,
                group.recordIds.map { it to (titles[it] ?: "#$it") }
            )
        }

    /** Reloads linked records' titles, which may have changed elsewhere; when the screen is shown again. */
    fun refreshLinks() = launchReporting {
        val state = _state.value ?: return@launchReporting
        val choices = loadChoices(state.fields)
        val linked = stores.titles.linkedValues(state.fields)
        val backlinks = if (state.isNew) emptyList() else loadBacklinks(state.record.id)
        _state.update {
            it?.copy(
                recordTitles = choices.mapValues { (_, c) -> c.titles },
                linkFacets = choices.mapValues { (_, c) -> c.facets },
                linkedValues = linked,
                backlinks = backlinks
            )
        }
    }

    private suspend fun loadChoices(fields: List<FieldDef>): Map<Long, LinkChoices> =
        fields.mapNotNull { field -> field.recordLink?.let { field.id to stores.titles.choices(it) } }.toMap()

    override fun onCleared() {
        saveInBackground()
    }

    companion object {
        private const val KEY_LOCKED = "locked"
        private const val KEY_RECORD = "shownRecord"
        private const val TAG = "RecordViewModel"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AnDbApplication
                val savedState = createSavedStateHandle()
                val route = savedState.toRoute<RecordRoute>()
                RecordViewModel(
                    RecordStores(app.repository, app.records, app.recordTitles),
                    app.appScope,
                    savedState,
                    app.browseOrder,
                    route.databaseId,
                    route.recordId,
                    route.prelink
                )
            }
        }
    }
}

/** Editor text of [record]'s values; links are kept apart, as [links]. */
private fun drafts(record: Record): Map<Long, String> =
    record.values.filterValues { it !is LinkValue }.mapValues { (_, v) -> FieldValues.toDraft(v) }

private fun links(fields: List<FieldDef>, record: Record): Map<Long, LinkValue?> =
    fields.filter { it.type == FieldType.LINK }.associate { it.id to record.values[it.id] as? LinkValue }
