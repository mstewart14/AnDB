package ca.stewartclan.andb.ui.list

import ca.stewartclan.andb.data.Condition
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.FieldType
import ca.stewartclan.andb.data.Filter
import ca.stewartclan.andb.data.Op
import ca.stewartclan.andb.data.Record
import ca.stewartclan.andb.data.ValueKind
import ca.stewartclan.andb.data.valueKind
import ca.stewartclan.andb.ui.record.FieldValues
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** Applies saved filters to records. Text compares without regard to case. */
object FilterMatcher {
    /**
     * Whether [record] passes [filter]. Conditions on fields that no longer exist, or not yet
     * complete, are ignored; a filter with none left passes everything.
     */
    fun matches(filter: Filter, fields: Map<Long, FieldDef>, record: Record): Boolean {
        val results = filter.conditions.mapNotNull { condition ->
            fields[condition.fieldId]?.takeIf { isComplete(condition, it) }?.let { matches(condition, it, record) }
        }
        return when {
            results.isEmpty() -> true
            filter.matchAll -> results.all { it }
            else -> results.any { it }
        }
    }

    /** Whether [condition] has what its operator needs, so it can be saved and applied. */
    fun isComplete(condition: Condition, field: FieldDef): Boolean = when {
        condition.op !in field.valueKind.ops -> false
        !condition.op.needsValue -> true
        field.valueKind == ValueKind.CHOICES -> condition.choices.isNotEmpty()
        field.valueKind == ValueKind.NUMBER -> number(condition.value) != null
        else -> condition.value.isNotBlank()
    }

    private fun matches(condition: Condition, field: FieldDef, record: Record): Boolean = when (field.valueKind) {
        ValueKind.CHECKBOX -> (record.values[field.id] == 1L) == (condition.op == Op.CHECKED)
        ValueKind.CHOICES -> choices(condition, record.choices[field.id].orEmpty())
        ValueKind.NUMBER -> compare(
            condition,
            (record.values[field.id] as? Number)?.toDouble(),
            number(condition.value)
        )
        ValueKind.TEXT -> text(condition, FieldValues.display(field, record.values[field.id]))
        ValueKind.TIMESTAMP -> {
            val stamp = if (field.type == FieldType.CREATED) record.created else record.modified
            compare(condition, localDate(stamp), condition.value)
        }
        ValueKind.DATE, ValueKind.TIME ->
            compare(condition, (record.values[field.id] as? String)?.takeIf { it.isNotBlank() }, condition.value)
    }

    private fun text(condition: Condition, shown: String): Boolean {
        val wanted = condition.value.trim()
        return when (condition.op) {
            Op.CONTAINS -> shown.contains(wanted, ignoreCase = true)
            Op.NOT_CONTAINS -> !shown.contains(wanted, ignoreCase = true)
            Op.IS -> shown.equals(wanted, ignoreCase = true)
            Op.IS_NOT -> !shown.equals(wanted, ignoreCase = true)
            Op.STARTS_WITH -> shown.startsWith(wanted, ignoreCase = true)
            Op.EMPTY -> shown.isBlank()
            Op.NOT_EMPTY -> shown.isNotBlank()
            else -> false
        }
    }

    /** Ordered comparison for numbers and ISO dates/times; an empty value only matches the empty tests. */
    private fun <T : Comparable<T>> compare(condition: Condition, actual: T?, wanted: T?): Boolean = when {
        condition.op == Op.EMPTY -> actual == null
        condition.op == Op.NOT_EMPTY -> actual != null
        actual == null || wanted == null -> condition.op == Op.IS_NOT && actual != wanted
        else -> {
            val c = actual.compareTo(wanted)
            when (condition.op) {
                Op.IS -> c == 0
                Op.IS_NOT -> c != 0
                Op.LESS -> c < 0
                Op.LESS_OR_EQUAL -> c <= 0
                Op.GREATER -> c > 0
                Op.GREATER_OR_EQUAL -> c >= 0
                else -> false
            }
        }
    }

    private fun choices(condition: Condition, chosen: List<String>): Boolean = when (condition.op) {
        Op.ANY_OF -> condition.choices.any { it in chosen }
        Op.ALL_OF -> chosen.containsAll(condition.choices)
        Op.NONE_OF -> condition.choices.none { it in chosen }
        Op.EMPTY -> chosen.isEmpty()
        Op.NOT_EMPTY -> chosen.isNotEmpty()
        else -> false
    }

    private fun number(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

    /** The local date (ISO) of a stored ISO instant. */
    private fun localDate(instant: String): String? = try {
        Instant.parse(instant).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    } catch (_: DateTimeParseException) {
        null
    }
}
