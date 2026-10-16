package ca.stewartclan.andb.data

/**
 * One record. [values] holds each stored field's column value by field id (String, Long or
 * Double, or null; a [LinkValue] for a Link field, or a list of them, in the order made, for one
 * allowing several); [choices] holds each multiple-choice List field's choices in the order made.
 */
data class Record(
    val id: Long,
    val created: String,
    val modified: String,
    val values: Map<Long, Any?>,
    val choices: Map<Long, List<String>>
)

/** Records [recordIds] of database [databaseId] whose Link [field] points at a given record. */
data class Backlinks(val databaseId: String, val databaseName: String, val field: FieldDef, val recordIds: List<Long>)

/** A record to import: values and choices as for [RecordRepository.insert]. */
data class ImportRow(val values: Map<Long, Any?>, val choices: Map<Long, List<String>>)

/** Why an imported row wasn't stored. */
enum class RowProblem { REQUIRED_MISSING, DUPLICATE_ID }

/** How an import went: how many rows were stored, and which (by index) were skipped and why. */
data class ImportOutcome(val imported: Int, val skipped: Map<Int, RowProblem>)
