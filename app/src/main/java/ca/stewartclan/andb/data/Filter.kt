package ca.stewartclan.andb.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A saved, named filter: records matching all ([matchAll]) or any of [conditions]. */
data class Filter(
    val id: Long,
    val position: Int,
    val name: String,
    val matchAll: Boolean,
    val conditions: List<Condition>
)

/**
 * One test on field [fieldId]: [op] with [value] (text, a number, or an ISO date/time), or with
 * [choices] for multiple-choice lists.
 */
@Serializable
data class Condition(val fieldId: Long, val op: Op, val value: String = "", val choices: List<String> = emptyList())

/** Condition operators. The serial names are stored, so they must not change. */
@Serializable
enum class Op(val needsValue: Boolean = true) {
    @SerialName("contains")
    CONTAINS,

    @SerialName("not_contains")
    NOT_CONTAINS,

    @SerialName("is")
    IS,

    @SerialName("is_not")
    IS_NOT,

    @SerialName("starts")
    STARTS_WITH,

    @SerialName("lt")
    LESS,

    @SerialName("le")
    LESS_OR_EQUAL,

    @SerialName("gt")
    GREATER,

    @SerialName("ge")
    GREATER_OR_EQUAL,

    @SerialName("empty")
    EMPTY(needsValue = false),

    @SerialName("not_empty")
    NOT_EMPTY(needsValue = false),

    @SerialName("checked")
    CHECKED(needsValue = false),

    @SerialName("unchecked")
    UNCHECKED(needsValue = false),

    @SerialName("any_of")
    ANY_OF,

    @SerialName("all_of")
    ALL_OF,

    @SerialName("none_of")
    NONE_OF
}

/** How a field's values are compared, which decides the operators it offers. */
enum class ValueKind(val ops: List<Op>) {
    TEXT(listOf(Op.CONTAINS, Op.NOT_CONTAINS, Op.IS, Op.IS_NOT, Op.STARTS_WITH, Op.EMPTY, Op.NOT_EMPTY)),
    NUMBER(
        listOf(
            Op.IS,
            Op.IS_NOT,
            Op.LESS,
            Op.LESS_OR_EQUAL,
            Op.GREATER,
            Op.GREATER_OR_EQUAL,
            Op.EMPTY,
            Op.NOT_EMPTY
        )
    ),
    DATE(
        listOf(
            Op.IS,
            Op.IS_NOT,
            Op.LESS,
            Op.GREATER,
            Op.LESS_OR_EQUAL,
            Op.GREATER_OR_EQUAL,
            Op.EMPTY,
            Op.NOT_EMPTY
        )
    ),
    TIME(DATE.ops),

    /** Created/modified: compared by their date, and never empty. */
    TIMESTAMP(listOf(Op.IS, Op.IS_NOT, Op.LESS, Op.GREATER, Op.LESS_OR_EQUAL, Op.GREATER_OR_EQUAL)),
    CHECKBOX(listOf(Op.CHECKED, Op.UNCHECKED)),
    CHOICES(listOf(Op.ANY_OF, Op.ALL_OF, Op.NONE_OF, Op.EMPTY, Op.NOT_EMPTY))
}

/** How [this] field's values are compared in filters. */
val FieldDef.valueKind: ValueKind
    get() = when {
        isMultiList -> ValueKind.CHOICES
        type == FieldType.BOOLEAN -> ValueKind.CHECKBOX
        type == FieldType.DATE -> ValueKind.DATE
        type == FieldType.TIME -> ValueKind.TIME
        type == FieldType.CREATED || type == FieldType.MODIFIED -> ValueKind.TIMESTAMP
        type == FieldType.ID -> if ((props as? IdProps)?.kind == IdKind.INCREMENT) ValueKind.NUMBER else ValueKind.TEXT
        storage == Storage.INTEGER || storage == Storage.REAL -> ValueKind.NUMBER
        else -> ValueKind.TEXT
    }

/** Whether records can be filtered by field [this]; not by Linked fields, whose values live elsewhere. */
val FieldDef.canFilter: Boolean get() = canSort
