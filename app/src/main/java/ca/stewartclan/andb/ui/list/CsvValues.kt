package ca.stewartclan.andb.ui.list

import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.FieldType
import ca.stewartclan.andb.data.IdKind
import ca.stewartclan.andb.data.IdProps
import ca.stewartclan.andb.data.LinkValue
import ca.stewartclan.andb.data.Record
import ca.stewartclan.andb.data.RecordRef
import ca.stewartclan.andb.data.Storage
import ca.stewartclan.andb.ui.common.RecordTitles
import ca.stewartclan.andb.ui.record.FieldValues

/** What a CSV cell turns into for a field. */
sealed interface CellValue {
    /** A value to store (null for empty). */
    data class Value(val value: Any?) : CellValue

    /** The choices of a multiple-choice List. */
    data class Choices(val choices: List<String>) : CellValue

    /** Not a valid value for the field; left empty and reported. */
    data object Invalid : CellValue
}

/** Fields CSV import can fill: ones the user enters, except photos, which a CSV can't carry. */
val FieldDef.canImport: Boolean
    get() = type.storage != Storage.NONE && !(type == FieldType.LINK && recordLink == null)

/**
 * Conversions between records and CSV cells. Export writes what the list shows, except in plain
 * forms where spreadsheets or a later import need them: ISO dates and times, yes/no, numbers
 * (not stars), and multiple choices or several linked titles joined by "; ". Text a spreadsheet
 * would take for a formula ("=…", "+…", "-…", "@…") gets a leading "'", which import removes.
 */
object CsvValues {
    const val CHOICE_SEPARATOR = "; "

    private val yes = setOf("yes", "y", "true", "1", "x", "✓")
    private val no = setOf("no", "n", "false", "0", "")

    /** Fields exported as columns: every field that shows something. */
    fun exported(fields: List<FieldDef>): List<FieldDef> = fields.filter { it.type != FieldType.SECTION }

    /** The cell for [field] of [record], given what the list [shown] for it. */
    fun export(field: FieldDef, record: Record, shown: String): String {
        val value = record.values[field.id]
        return when {
            field.isMultiList -> record.choices[field.id].orEmpty().joinToString(CHOICE_SEPARATOR)
            field.type == FieldType.BOOLEAN -> if (value == 1L) "yes" else "no"
            field.type == FieldType.CREATED -> record.created
            field.type == FieldType.MODIFIED -> record.modified
            field.type in setOf(FieldType.DATE, FieldType.TIME, FieldType.RATING) -> FieldValues.toDraft(value)
            field.storage == Storage.INTEGER || field.storage == Storage.REAL -> shown
            else -> shown.asText()
        }.let { if (field.isMultiList) it.asText() else it }
    }

    /** This text as a cell a spreadsheet won't run: one that would start a formula starts with "'". */
    private fun String.asText(): String = if (firstOrNull() in FORMULA_STARTS) "'$this" else this

    /** A cell as exported, without the "'" export put before text that looks like a formula. */
    private fun String.fromText(): String =
        if (startsWith("'") && getOrNull(1) in FORMULA_STARTS) substring(1) else this

    /** What a spreadsheet takes to start a formula (or, for tab and return, to hide one). */
    private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')

    /**
     * What [text] means for [field]. A record link needs [titles] of the records it can link to
     * (by record id): it links only to the one record with that title, ignoring case.
     */
    fun import(field: FieldDef, text: String, titles: Map<Long, String> = emptyMap()): CellValue {
        val cell = text.trim().fromText()
        return when {
            field.isMultiList -> CellValue.Choices(
                cell.split(';').map {
                    it.trim()
                }.filter { it.isNotEmpty() }.distinct()
            )
            field.type == FieldType.BOOLEAN -> when (cell.lowercase()) {
                in yes -> CellValue.Value(1L)
                in no -> CellValue.Value(0L)
                else -> CellValue.Invalid
            }
            cell.isEmpty() -> CellValue.Value(null)
            field.isMultiLink -> recordLinks(field, cell, titles)
            field.type == FieldType.LINK -> recordLink(field, cell, titles)
            field.type == FieldType.ID -> id(field, cell)
            else -> typed(field, cell)
        }
    }

    private fun typed(field: FieldDef, cell: String): CellValue {
        val value: Any? = when (field.type) {
            FieldType.DATE -> FieldValues.parseDate(cell)?.toString()
            FieldType.TIME -> FieldValues.parseTime(cell)?.let { "%02d:%02d".format(it.hour, it.minute) }
            FieldType.RATING -> cell.toLongOrNull()?.takeIf { it in 0..FieldValues.MAX_RATING }
            FieldType.SCALE -> cell.toLongOrNull()?.takeIf { it in 0..FieldValues.MAX_SCALE }
            else -> when (field.storage) {
                Storage.INTEGER -> cell.toLongOrNull()
                Storage.REAL -> cell.replace(',', '.').toDoubleOrNull()
                else -> cell
            }
        }
        return if (value == null) CellValue.Invalid else CellValue.Value(value)
    }

    /** An ID as written by export: an increment's prefix and padding are removed. */
    private fun id(field: FieldDef, cell: String): CellValue {
        val props = field.props as? IdProps
        val number = props?.let { cell.removePrefix(it.prefix).toLongOrNull() }
        return when {
            props == null -> CellValue.Invalid
            props.kind != IdKind.INCREMENT -> CellValue.Value(cell)
            number == null || number < 0 -> CellValue.Invalid
            else -> CellValue.Value(number)
        }
    }

    private fun recordLink(field: FieldDef, cell: String, titles: Map<Long, String>): CellValue =
        link(field, cell, titles)?.let { CellValue.Value(it) } ?: CellValue.Invalid

    /** Titles separated by ";", each linking as [recordLink] does; invalid unless every one does. */
    private fun recordLinks(field: FieldDef, cell: String, titles: Map<Long, String>): CellValue {
        val wanted = cell.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        val links = wanted.mapNotNull { link(field, it, titles) }.distinctBy { it.uri }
        val allFound = links.size == wanted.distinctBy { it.lowercase() }.size
        return if (allFound) CellValue.Value(links) else CellValue.Invalid
    }

    /** The link to the one record titled [title], ignoring case; null if none or several are. */
    private fun link(field: FieldDef, title: String, titles: Map<Long, String>): LinkValue? {
        val databaseId = field.recordLink?.databaseId
        val match = RecordTitles.matching(title, titles).singleOrNull()
        return if (databaseId == null || match == null) {
            null
        } else {
            LinkValue(RecordRef(databaseId, match).uri, titles.getValue(match), null)
        }
    }
}
