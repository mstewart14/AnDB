package ca.stewartclan.andb.ui.common

import android.database.sqlite.SQLiteException
import ca.stewartclan.andb.data.DatabaseRepository
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.LinkProps
import ca.stewartclan.andb.data.LinkedProps
import ca.stewartclan.andb.data.RecordRepository
import ca.stewartclan.andb.data.canBeColumn
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
     * What field [fieldId] (the display field when null) shows for every record of database
     * [databaseId], by record id; a record with nothing to show is "#id". Empty if the database
     * is gone.
     */
    suspend fun values(databaseId: String, fieldId: Long?): Map<Long, String> = try {
        val field = displayField(repository.fields(databaseId), fieldId)
        records.records(databaseId).associate { record ->
            record.id to (field?.let { FieldValues.shown(it, record) }.orEmpty().ifBlank { "#${record.id}" })
        }
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
        /** Field [id] of [fields], or by default the first that shows something of the record's own. */
        fun displayField(fields: List<FieldDef>, id: Long?): FieldDef? = fields.find { it.id == id }
            ?: fields.firstOrNull { it.canBeColumn && it.props !is LinkProps && it.props !is LinkedProps }
    }
}
