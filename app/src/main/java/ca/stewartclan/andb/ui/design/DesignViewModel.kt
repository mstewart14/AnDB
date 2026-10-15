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
import ca.stewartclan.andb.data.LinkProps
import ca.stewartclan.andb.data.LinkedProps
import ca.stewartclan.andb.data.Repoint
import ca.stewartclan.andb.ui.common.NameProblem
import ca.stewartclan.andb.ui.common.NameValidator
import ca.stewartclan.andb.ui.common.RecordTitles
import ca.stewartclan.andb.ui.common.ReportingViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What relinking by title would do when Link field [fieldId] is pointed at [link]'s database. */
data class RelinkPreview(val fieldId: Long, val link: LinkProps, val plan: RelinkPlan)

class DesignViewModel(
    private val repository: DatabaseRepository,
    private val titles: RecordTitles,
    val databaseId: String
) : ReportingViewModel() {

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

    /** Adds [entered] (its id and position are ignored) after the existing fields. */
    fun addField(entered: FieldDef) = change {
        repository.addField(databaseId, entered.name.trim(), entered.type, entered.props, entered.required)
    }

    /**
     * Saves [entered] over [field], if anything changed. A Link to records pointed at another
     * database keeps the links found there by title if [relink], and its Linked fields show the
     * field of the same name there.
     */
    fun updateField(field: FieldDef, entered: FieldDef, relink: Boolean) = change {
        val new = entered.copy(id = field.id, position = field.position, name = entered.name.trim())
        if (new != field) repository.updateField(databaseId, new, repoint(field, new, relink))
    }

    private val _relinkPreview = MutableStateFlow<RelinkPreview?>(null)

    /** What relinking would do for the Link field being edited, once worked out. */
    val relinkPreview: StateFlow<RelinkPreview?> = _relinkPreview.asStateFlow()

    private var previewJob: Job? = null

    /** Works out [relinkPreview] for pointing Link field [field] at [link]'s database. */
    fun loadRelinkPreview(field: FieldDef, link: LinkProps) {
        // Only the latest choice counts; an earlier, slower one mustn't land after it.
        previewJob?.cancel()
        previewJob = launchReporting {
            _relinkPreview.value = null
            _relinkPreview.value = RelinkPreview(field.id, link, relinkPlan(field, link))
        }
    }

    private suspend fun relinkPlan(field: FieldDef, link: LinkProps): RelinkPlan =
        Relink.plan(repository.linkTitles(databaseId, field.id), titles.titles(link), link.databaseId.orEmpty())

    private suspend fun repoint(old: FieldDef, new: FieldDef, relink: Boolean): Repoint? {
        val to = new.recordLink?.takeIf { isRepoint(old, new) } ?: return null
        // The old database may be gone, and with it the names of the fields Linked fields showed.
        val oldTargets = runCatching { repository.fields(old.recordLink?.databaseId.orEmpty()) }.getOrNull()
        val newTargets = repository.fields(to.databaseId.orEmpty())
        return Repoint(
            links = if (relink) relinkPlan(old, to).links else null,
            linkedTargets = Relink.linkedTargets(fields.value.orEmpty(), old.id, oldTargets, newTargets)
        )
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

    private val _linkedTargets = MutableStateFlow<Map<Long, String>>(emptyMap())

    /** For each Linked field, the name of the field it shows (in the linked database). */
    val linkedTargets: StateFlow<Map<Long, String>> = _linkedTargets.asStateFlow()

    private suspend fun reload() {
        val fields = repository.fields(databaseId)
        _fields.value = fields
        val byId = fields.associateBy { it.id }
        _linkedTargets.value = fields.mapNotNull { field ->
            val props = field.props as? LinkedProps ?: return@mapNotNull null
            val target = byId[props.linkFieldId]?.recordLink?.databaseId ?: return@mapNotNull null
            val targetFields = runCatching { repository.fields(target) }.getOrNull() ?: return@mapNotNull null
            RecordTitles.displayField(targetFields, props.targetFieldId)?.let { field.id to it.name }
        }.toMap()
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
                val app = this[APPLICATION_KEY] as AnDbApplication
                DesignViewModel(
                    app.repository,
                    app.recordTitles,
                    createSavedStateHandle().toRoute<DesignRoute>().databaseId
                )
            }
        }
    }
}
