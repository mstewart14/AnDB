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
