package ca.stewartclan.andb.data

import android.database.sqlite.SQLiteDatabase
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Saved filters of AnDB databases, and which one the list applies. */
class FilterRepository(private val databases: DatabaseRepository) {
    private val json = Json { ignoreUnknownKeys = true }
    private val conditionsSerializer = ListSerializer(Condition.serializer())

    /** Filters of database [id] in order, without conditions on fields that no longer exist. */
    suspend fun filters(id: String): List<Filter> = databases.read(id) { readFilters() }

    /** The filter the list applies, or null for all records. */
    suspend fun currentFilterId(id: String): Long? = databases.read(id) {
        getMeta(DatabaseFile.META_CURRENT_FILTER)?.toLongOrNull()
    }

    suspend fun setCurrentFilter(id: String, filterId: Long?) = databases.write(id) {
        if (filterId == null) {
            deleteMeta(DatabaseFile.META_CURRENT_FILTER)
        } else {
            setMeta(DatabaseFile.META_CURRENT_FILTER, filterId.toString())
        }
    }

    /** Saves [filter], adding it after the others if its id is 0, and returns its id. */
    suspend fun save(id: String, filter: Filter): Long = databases.write(id) {
        val conditions = json.encodeToString(conditionsSerializer, filter.conditions)
        val matchAll = if (filter.matchAll) 1 else 0
        if (filter.id == 0L) {
            val position = longQuery("SELECT COALESCE(MAX(position) + 1, 0) FROM andb_filter")
            execSQL(
                "INSERT INTO andb_filter (position, name, match_all, conditions) VALUES (?, ?, ?, ?)",
                arrayOf(position, filter.name, matchAll, conditions)
            )
            longQuery("SELECT last_insert_rowid()")
        } else {
            execSQL(
                "UPDATE andb_filter SET name = ?, match_all = ?, conditions = ? WHERE id = ?",
                arrayOf(filter.name, matchAll, conditions, filter.id)
            )
            filter.id
        }
    }

    /** Deletes filter [filterId]; if the list was applying it, the list shows all records. */
    suspend fun delete(id: String, filterId: Long) = databases.write(id) {
        execSQL("DELETE FROM andb_filter WHERE id = ?", arrayOf(filterId))
        if (getMeta(DatabaseFile.META_CURRENT_FILTER) == filterId.toString()) {
            deleteMeta(DatabaseFile.META_CURRENT_FILTER)
        }
    }

    private fun SQLiteDatabase.readFilters(): List<Filter> {
        val fieldIds = readFields().map { it.id }.toSet()
        return rawQuery("SELECT id, position, name, match_all, conditions FROM andb_filter ORDER BY position", null)
            .use { c ->
                val id = c.getColumnIndexOrThrow("id")
                val position = c.getColumnIndexOrThrow("position")
                val name = c.getColumnIndexOrThrow("name")
                val matchAll = c.getColumnIndexOrThrow("match_all")
                val conditions = c.getColumnIndexOrThrow("conditions")
                buildList {
                    while (c.moveToNext()) {
                        add(
                            Filter(
                                id = c.getLong(id),
                                position = c.getInt(position),
                                name = c.getString(name),
                                matchAll = c.getInt(matchAll) != 0,
                                conditions = json.decodeFromString(conditionsSerializer, c.getString(conditions))
                                    .filter { it.fieldId in fieldIds }
                            )
                        )
                    }
                }
            }
    }
}
