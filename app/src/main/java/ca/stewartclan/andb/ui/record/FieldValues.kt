package ca.stewartclan.andb.ui.record

import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.FieldType
import ca.stewartclan.andb.data.IdProps
import ca.stewartclan.andb.data.Record
import ca.stewartclan.andb.data.Storage
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

/**
 * Conversions between stored values (see [ca.stewartclan.andb.data.Record]), the text a field
 * editor works with, and what lists show. Dates are stored as ISO `yyyy-MM-dd`, times as `HH:mm`,
 * checkboxes as 1/0.
 */
object FieldValues {
    const val MAX_RATING = 5
    const val MAX_SCALE = 10

    // Made on use, so they follow the device's current language and region.
    private val dateFormat get() = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val timeFormat get() = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val timestampFormat get() = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

    /** The editor text for a stored [value]; empty when unset. */
    fun toDraft(value: Any?): String = when (value) {
        null -> ""
        is Double -> value.toBigDecimal().stripTrailingZeros().toPlainString()
        else -> value.toString()
    }

    /** The value to store for editor [text]: null when blank or not (yet) a valid number. */
    fun fromDraft(field: FieldDef, text: String): Any? = when {
        field.type == FieldType.BOOLEAN -> if (text == "1") 1L else 0L
        text.isBlank() -> null
        field.storage == Storage.INTEGER -> text.toLongOrNull()
        field.storage == Storage.REAL -> text.replace(',', '.').toDoubleOrNull()
        else -> text.trim()
    }

    /**
     * Keeps only what can be part of a number as the user types: digits, a leading minus and, if
     * [decimal], one decimal separator.
     */
    fun filterNumber(text: String, decimal: Boolean): String {
        var separatorSeen = false
        return text.filterIndexed { i, c ->
            when {
                c.isDigit() -> true
                c == '-' -> i == 0
                decimal && (c == '.' || c == ',') && !separatorSeen -> true.also { separatorSeen = true }
                else -> false
            }
        }
    }

    /** One-line text for [value] of [field], as list rows and read-only displays show it. */
    fun display(field: FieldDef, value: Any?): String = when {
        value == null -> ""
        // Several links, by their saved titles.
        value is List<*> -> value.joinToString(", ")
        field.type == FieldType.BOOLEAN -> if (value == 1L) "✓" else ""
        field.type == FieldType.DATE -> parse(value) { dateFormat.format(LocalDate.parse(it)) }
        field.type == FieldType.TIME -> parse(value) { timeFormat.format(LocalTime.parse(it)) }
        field.type == FieldType.RATING -> (value as? Long)?.let { stars(it.toInt()) } ?: value.toString()
        field.type == FieldType.ID && value is Long -> (field.props as? IdProps)?.format(value) ?: value.toString()
        else -> toDraft(value)
    }

    /** What [field] of [record] shows as one line of text, e.g. in a list cell. */
    fun shown(field: FieldDef, record: Record): String = when {
        field.isMultiList -> record.choices[field.id].orEmpty().joinToString(", ")
        field.type == FieldType.CREATED -> timestamp(record.created)
        field.type == FieldType.MODIFIED -> timestamp(record.modified)
        else -> display(field, record.values[field.id])
    }

    fun stars(rating: Int): String = "★".repeat(rating.coerceIn(0, MAX_RATING)) +
        "☆".repeat(MAX_RATING - rating.coerceIn(0, MAX_RATING))

    /** A stored ISO timestamp (created/modified) in the device's time zone and format. */
    fun timestamp(iso: String): String =
        parse(iso) { timestampFormat.format(Instant.parse(it).atZone(ZoneId.systemDefault())) }

    fun parseDate(text: String): LocalDate? = try {
        LocalDate.parse(text)
    } catch (_: DateTimeParseException) {
        null
    }

    fun parseTime(text: String): LocalTime? = try {
        LocalTime.parse(text)
    } catch (_: DateTimeParseException) {
        null
    }

    /** Formats [value] with [format], or shows it as stored if it isn't in the expected form. */
    private fun parse(value: Any, format: (String) -> String): String = try {
        format(value.toString())
    } catch (_: DateTimeParseException) {
        value.toString()
    }
}
