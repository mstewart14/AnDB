package ca.stewartclan.andb.ui.list

import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import ca.stewartclan.andb.AnDbApplication
import ca.stewartclan.andb.data.DatabaseRepository
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.FieldType
import ca.stewartclan.andb.data.ImportRow
import ca.stewartclan.andb.data.RecordRepository
import ca.stewartclan.andb.data.RowProblem
import ca.stewartclan.andb.data.csv.Csv
import ca.stewartclan.andb.ui.common.NameProblem
import ca.stewartclan.andb.ui.common.NameValidator
import ca.stewartclan.andb.ui.common.RecordTitles
import ca.stewartclan.andb.ui.common.ReportingViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

/** Navigation route for importing CSV file [uri] into database [databaseId]. */
@Serializable
data class CsvImportRoute(val databaseId: String, val uri: String)

/** Where a CSV column goes: nowhere, a new text field, or an existing field. */
sealed interface ColumnTarget {
    data object Skip : ColumnTarget

    data object NewField : ColumnTarget

    data class Field(val fieldId: Long) : ColumnTarget
}

/** A value that couldn't be read: data row [row] (1 = first), column [column], text [text]. */
data class BadValue(val row: Int, val column: String, val text: String)

/** What an import did. */
data class ImportReport(
    val imported: Int,
    val missingRequired: Int,
    val duplicateIds: Int,
    val badValues: List<BadValue>
)

data class CsvImportState(
    val cells: List<List<String>>,
    val firstRowIsHeader: Boolean,
    /** Fields CSV columns can go to. */
    val fields: List<FieldDef>,
    /** Per column. */
    val targets: List<ColumnTarget>,
    val keepIds: Boolean = false,
    val importing: Boolean = false,
    val report: ImportReport? = null
) {
    val columnCount: Int get() = cells.maxOfOrNull { it.size } ?: 0

    /** Column names: the header row's, or "Column n". */
    val names: List<String>
        get() = List(columnCount) { i ->
            cells.firstOrNull()?.getOrNull(i)?.trim()?.takeIf { firstRowIsHeader && it.isNotEmpty() }
                ?: "Column ${i + 1}"
        }

    val dataRows: List<List<String>> get() = if (firstRowIsHeader) cells.drop(1) else cells

    /** Whether some column goes to an ID field, so keeping the file's IDs is a choice. */
    val mapsId: Boolean
        get() = targets.any { t -> t is ColumnTarget.Field && fields.find { it.id == t.fieldId }?.type == FieldType.ID }

    val canImport: Boolean get() = !importing && report == null && targets.any { it != ColumnTarget.Skip }
}

class CsvImportViewModel(
    private val repository: DatabaseRepository,
    private val records: RecordRepository,
    private val titles: RecordTitles,
    private val readFile: suspend () -> String,
    private val databaseId: String
) : ReportingViewModel() {
    private val _state = MutableStateFlow<CsvImportState?>(null)

    /** `null` while the file is read. */
    val state: StateFlow<CsvImportState?> = _state.asStateFlow()

    init {
        launchReporting {
            val cells = Csv.read(readFile())
            val fields = repository.fields(databaseId).filter { it.canImport }
            _state.value = CsvImportState(cells, firstRowIsHeader = true, fields, emptyList()).let {
                it.copy(targets = autoTargets(it))
            }
        }
    }

    fun setFirstRowIsHeader(header: Boolean) = _state.update { state ->
        state?.copy(firstRowIsHeader = header)?.let { it.copy(targets = autoTargets(it)) }
    }

    fun setTarget(column: Int, target: ColumnTarget) = _state.update { state ->
        state?.copy(targets = state.targets.mapIndexed { i, t -> if (i == column) target else t })
    }

    fun setKeepIds(keep: Boolean) = _state.update { it?.copy(keepIds = keep) }

    /**
     * Columns go to the field of the same name (ignoring case); the rest are skipped, or become new
     * text fields when the database has none to fill yet.
     */
    private fun autoTargets(state: CsvImportState): List<ColumnTarget> = state.names.map { name ->
        state.fields.find { it.name.equals(name, ignoreCase = true) }?.let { ColumnTarget.Field(it.id) }
            ?: if (state.fields.isEmpty()) ColumnTarget.NewField else ColumnTarget.Skip
    }

    fun import() {
        val state = _state.value?.takeIf { it.canImport } ?: return
        _state.value = state.copy(importing = true)
        launchReporting {
            try {
                val columnFields = fieldsFor(state)
                val linkTitles = columnFields.filterNotNull().distinct()
                    .mapNotNull { f -> f.recordLink?.let { f.id to titles.titles(it) } }.toMap()
                val bad = mutableListOf<BadValue>()
                val rows = state.dataRows.mapIndexed { r, cells ->
                    toRow(state, columnFields, cells, linkTitles) { column, text ->
                        bad += BadValue(r + 1, column, text)
                    }
                }
                val outcome = records.insertAll(databaseId, rows, state.keepIds)
                _state.value = state.copy(
                    report = ImportReport(
                        imported = outcome.imported,
                        missingRequired = outcome.skipped.values.count { it == RowProblem.REQUIRED_MISSING },
                        duplicateIds = outcome.skipped.values.count { it == RowProblem.DUPLICATE_ID },
                        badValues = bad
                    )
                )
            } finally {
                _state.update { it?.copy(importing = false) }
            }
        }
    }

    /** Each column's field; "new text field" columns get one now, named after the column. */
    private suspend fun fieldsFor(state: CsvImportState): List<FieldDef?> {
        val taken = repository.fields(databaseId).map { it.name }.toMutableList()
        return state.targets.mapIndexed { i, target ->
            when (target) {
                ColumnTarget.Skip -> null
                is ColumnTarget.Field -> state.fields.find { it.id == target.fieldId }
                ColumnTarget.NewField -> {
                    val name = uniqueName(state.names[i], taken).also { taken += it }
                    repository.addField(databaseId, name, FieldType.STRING)
                }
            }
        }
    }

    /** [base] if no field has that name yet, otherwise "[base] (2)", "[base] (3)"… */
    private fun uniqueName(base: String, taken: List<String>): String {
        val names = sequenceOf(base.take(NameValidator.MAX_LENGTH)) + generateSequence(2) { it + 1 }
            .map { n -> "${base.take(NameValidator.MAX_LENGTH - NUMBER_SUFFIX_LENGTH)} ($n)" }
        return names.first { NameValidator.check(it, taken) == NameProblem.OK }
    }

    private fun toRow(
        state: CsvImportState,
        columnFields: List<FieldDef?>,
        cells: List<String>,
        linkTitles: Map<Long, Map<Long, String>>,
        onBad: (column: String, text: String) -> Unit
    ): ImportRow {
        val values = mutableMapOf<Long, Any?>()
        val choices = mutableMapOf<Long, List<String>>()
        columnFields.forEachIndexed { i, field ->
            if (field == null) return@forEachIndexed
            val text = cells.getOrElse(i) { "" }
            when (val cell = CsvValues.import(field, text, linkTitles[field.id].orEmpty())) {
                is CellValue.Value -> values[field.id] = cell.value
                is CellValue.Choices -> choices[field.id] = cell.choices
                CellValue.Invalid -> onBad(state.names[i], text)
            }
        }
        return ImportRow(values, choices)
    }

    companion object {
        /** Room left for " (n)" when a new field's name is taken. */
        private const val NUMBER_SUFFIX_LENGTH = 4

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AnDbApplication
                val route = createSavedStateHandle().toRoute<CsvImportRoute>()
                CsvImportViewModel(
                    app.repository,
                    app.records,
                    app.recordTitles,
                    { app.files.readText(route.uri.toUri()) },
                    route.databaseId
                )
            }
        }
    }
}
