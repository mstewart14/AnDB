package ca.stewartclan.andb.ui.common

import android.database.sqlite.SQLiteException
import ca.stewartclan.andb.data.DatabaseRepository
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.LinkProps
import ca.stewartclan.andb.data.RecordRepository
import ca.stewartclan.andb.data.canBeColumn
import ca.stewartclan.andb.ui.record.FieldValues

/** How linked records are shown: each by the value of its database's display field. */
class RecordTitles(private val repository: DatabaseRepository, private val records: RecordRepository) {

    /**
     * The display text of every record of [link]'s database, by record id. Empty if that
     * database is gone, so links to it show their snapshot instead.
     */
    suspend fun titles(link: LinkProps): Map<Long, String> {
        val databaseId = link.databaseId ?: return emptyMap()
        return try {
            val display = displayField(repository.fields(databaseId), link.displayFieldId)
            records.records(databaseId).associate { record ->
                record.id to (display?.let { FieldValues.shown(it, record) }.orEmpty().ifBlank { "#${record.id}" })
            }
        } catch (_: SQLiteException) {
            emptyMap()
        } catch (_: IllegalStateException) {
            emptyMap()
        }
    }

    companion object {
        /** Field [id] of [fields], or by default the first that shows something and isn't itself a link. */
        fun displayField(fields: List<FieldDef>, id: Long?): FieldDef? =
            fields.find { it.id == id } ?: fields.firstOrNull { it.canBeColumn && it.props !is LinkProps }
    }
}
