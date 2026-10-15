package ca.stewartclan.andb.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A named way of listing records (Pilot-DB's list views): which fields show as columns, in what
 * order and width, and how records are sorted.
 */
data class ListView(
    val id: Long,
    val position: Int,
    val name: String,
    /** Columns in display order; empty means every field that can show, following the design. */
    val columns: List<ViewColumn>,
    /** Sort keys, most significant first; empty means oldest record first. */
    val sort: List<SortKey>
) {
    val showsAllFields: Boolean get() = columns.isEmpty()
}

@Serializable
data class ViewColumn(val fieldId: Long, val width: ColumnWidth = ColumnWidth.MEDIUM)

@Serializable
enum class ColumnWidth {
    @SerialName("s")
    NARROW,

    @SerialName("m")
    MEDIUM,

    @SerialName("l")
    WIDE
}

@Serializable
data class SortKey(val fieldId: Long, val descending: Boolean = false, val caseSensitive: Boolean = false)

/** Whether field [this] can be a list column: it shows something per record. */
val FieldDef.canBeColumn: Boolean
    get() = type != FieldType.SECTION

/** Whether records can be sorted by field [this]; not by Linked fields, whose values live elsewhere. */
val FieldDef.canSort: Boolean get() = canBeColumn && type != FieldType.LINKED
