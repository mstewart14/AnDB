package ca.stewartclan.andb.data

/**
 * One record. [values] holds each stored field's column value by field id (String, Long or
 * Double, or null); [choices] holds each multiple-choice List field's choices in the order made.
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
