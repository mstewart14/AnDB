package ca.stewartclan.andb.ui.common

import android.database.sqlite.SQLiteException
import ca.stewartclan.andb.data.DatabaseRepository
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.FieldType
import ca.stewartclan.andb.data.LinkProps
import ca.stewartclan.andb.data.LinkedProps
import ca.stewartclan.andb.data.ListProps
import ca.stewartclan.andb.data.Record
import ca.stewartclan.andb.data.RecordRepository
import ca.stewartclan.andb.data.canBeColumn
import ca.stewartclan.andb.data.canFilter
import ca.stewartclan.andb.ui.record.FieldValues

/** How linked records are shown: each by the value of its database's display field. */
class RecordTitles(private val repository: DatabaseRepository, private val records: RecordRepository) {

    /**
     * The display text of every record of [link]'s database, by record id. Empty if that
     * database is gone, so links to it show their snapshot instead.
     */
    suspend fun titles(link: LinkProps): Map<Long, String> =
        link.databaseId?.let { values(it, link.displayFieldId) }.orEmpty()

    /**
     * What a record chooser for [link] offers: the titles, as [titles], plus the fields of its
     * database worth narrowing the choice by. Empty if that database is gone.
     */
    suspend fun choices(link: LinkProps): LinkChoices = try {
        val databaseId = link.databaseId ?: return LinkChoices()
        val fields = repository.fields(databaseId)
        val shown = displayField(fields, link.displayFieldId)
        val all = records.records(databaseId)
        LinkChoices(
            titles = all.associate { it.id to title(shown, it) },
            facets = fields.filter { it.id != shown?.id && it.canFacet }.mapNotNull { facet(it, all) }
        )
    } catch (_: SQLiteException) {
        LinkChoices()
    } catch (_: IllegalStateException) {
        LinkChoices()
    }

    /**
     * What field [fieldId] (the display field when null) shows for every record of database
     * [databaseId], by record id; a record with nothing to show is "#id". Empty if the database
     * is gone.
     */
    suspend fun values(databaseId: String, fieldId: Long?): Map<Long, String> = try {
        val field = displayField(repository.fields(databaseId), fieldId)
        records.records(databaseId).associate { it.id to title(field, it) }
    } catch (_: SQLiteException) {
        emptyMap()
    } catch (_: IllegalStateException) {
        emptyMap()
    }

    /**
     * For each Linked field among [fields]: what its target field shows, by linked record id.
     * Keyed by the Linked field's id.
     */
    suspend fun linkedValues(fields: List<FieldDef>): Map<Long, Map<Long, String>> {
        val byId = fields.associateBy { it.id }
        return fields.mapNotNull { field ->
            val props = field.props as? LinkedProps ?: return@mapNotNull null
            val databaseId = byId[props.linkFieldId]?.recordLink?.databaseId ?: return@mapNotNull null
            field.id to values(databaseId, props.targetFieldId)
        }.toMap()
    }

    companion object {
        /** What [field] shows for [record], or "#id" when nothing. */
        private fun title(field: FieldDef?, record: Record): String =
            field?.let { FieldValues.shown(it, record) }.orEmpty().ifBlank { "#${record.id}" }

        /** The ids of the records in [titles] titled [title], ignoring case and surrounding spaces. */
        fun matching(title: String, titles: Map<Long, String>): Set<Long> =
            title.trim().let { wanted -> titles.filterValues { it.trim().equals(wanted, ignoreCase = true) }.keys }

        /** Most values a field may have and still narrow a record chooser. */
        const val MAX_FACET_VALUES = 30

        /** Field [id] of [fields], or by default the first that shows something of the record's own. */
        fun displayField(fields: List<FieldDef>, id: Long?): FieldDef? = fields.find { it.id == id }
            ?: fields.firstOrNull { it.canBeColumn && it.props !is LinkProps && it.props !is LinkedProps }

        /**
         * [field] as a facet of [records], or null if it doesn't sort them into groups: it needs
         * 2 to [MAX_FACET_VALUES] values, fewer than there are records. Values of a List field
         * keep its order; others are alphabetical.
         */
        fun facet(field: FieldDef, records: List<Record>): Facet? {
            val byRecord = records.associate { record ->
                record.id to if (field.isMultiList) {
                    record.choices[field.id].orEmpty()
                } else {
                    listOf(FieldValues.shown(field, record).trim()).filter { it.isNotEmpty() }
                }
            }
            val values = byRecord.values.flatten().distinct()
            if (values.size !in 2..MAX_FACET_VALUES || values.size >= records.size) return null
            val items = (field.props as? ListProps)?.items.orEmpty()
            val order = compareBy<String> { items.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                .thenComparing(String.CASE_INSENSITIVE_ORDER)
            return Facet(field.id, field.name, values.sortedWith(order), byRecord)
        }

        /** Fields that can narrow a chooser: text-like values shown as they're stored. */
        private val FieldDef.canFacet: Boolean
            get() = when {
                !canFilter || props is LinkProps || props is LinkedProps -> false
                else -> type !in NOT_FACETS
            }

        private val NOT_FACETS =
            setOf(FieldType.BOOLEAN, FieldType.CREATED, FieldType.MODIFIED, FieldType.ID, FieldType.NOTE)
    }
}

/** What a record chooser offers: record titles by record id, and [facets] to narrow them by. */
data class LinkChoices(val titles: Map<Long, String> = emptyMap(), val facets: List<Facet> = emptyList())

/**
 * A field [name] of the linked database whose few [values] sort its records into groups;
 * [byRecord] holds each record's values (several for a multiple-choice List, none if empty).
 */
data class Facet(val fieldId: Long, val name: String, val values: List<String>, val byRecord: Map<Long, List<String>>)
