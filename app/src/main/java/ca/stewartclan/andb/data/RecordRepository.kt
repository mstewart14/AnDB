package ca.stewartclan.andb.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

/** Records of AnDB databases. Changes go through [databases] so they're serialised with design changes. */
class RecordRepository(private val databases: DatabaseRepository) {

    /** All records of database [id], oldest first. */
    suspend fun records(id: String): List<Record> = databases.read(id) { readRecords(readFields(), null) }

    /** Ids of all records of database [id], oldest first. */
    suspend fun recordIds(id: String): List<Long> = databases.read(id) {
        rawQuery("SELECT _id FROM record ORDER BY _id", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0)) }
        }
    }

    suspend fun record(id: String, recordId: Long): Record? =
        databases.read(id) { readRecords(readFields(), recordId).firstOrNull() }

    /** Adds an empty record, assigning its ID fields, and returns its record id. */
    suspend fun create(id: String): Long = databases.write(id) {
        val now = databases.clock().toString()
        execSQL("INSERT INTO record (_created, _modified) VALUES (?, ?)", arrayOf(now, now))
        val recordId = longQuery("SELECT last_insert_rowid()")
        assignIds(readFields(), recordId)
        recordId
    }

    /**
     * Saves the given values and choices of record [recordId] and updates its modified time. Fields
     * not given, ID fields and fields that no longer hold values are left alone.
     */
    suspend fun save(id: String, recordId: Long, values: Map<Long, Any?>, choices: Map<Long, List<String>>) =
        databases.write(id) {
            val fields = readFields().associateBy { it.id }
            val columns = values.filterKeys { fieldId ->
                fields[fieldId]?.let { it.storage != Storage.NONE && it.type != FieldType.ID } == true
            }
            val sets = columns.keys.joinToString("") { "${DatabaseFile.columnName(it)} = ?, " }
            execSQL(
                "UPDATE record SET ${sets}_modified = ? WHERE _id = ?",
                (columns.values + databases.clock().toString() + recordId).toTypedArray()
            )
            choices.filterKeys { fields[it]?.isMultiList == true }.forEach { (fieldId, chosen) ->
                writeChoices(fieldId, recordId, chosen)
            }
        }

    suspend fun delete(id: String, recordId: Long) = databases.write(id) {
        readFields().filter { it.isMultiList }.forEach {
            execSQL("DELETE FROM ${DatabaseFile.listTable(it.id)} WHERE record_id = ?", arrayOf(recordId))
        }
        execSQL("DELETE FROM record WHERE _id = ?", arrayOf(recordId))
    }

    /** Copies record [recordId] (new IDs and timestamps) and returns the copy's record id. */
    suspend fun duplicate(id: String, recordId: Long): Long = databases.write(id) {
        val fields = readFields()
        val copied = fields.filter { it.storage != Storage.NONE && it.type != FieldType.ID }
            .joinToString("") { ", ${DatabaseFile.columnName(it.id)}" }
        val now = databases.clock().toString()
        execSQL(
            "INSERT INTO record (_created, _modified$copied) SELECT ?, ?$copied FROM record WHERE _id = ?",
            arrayOf(now, now, recordId)
        )
        val copy = longQuery("SELECT last_insert_rowid()")
        fields.filter { it.isMultiList }.forEach {
            execSQL(
                "INSERT INTO ${DatabaseFile.listTable(it.id)} (record_id, value) " +
                    "SELECT ?, value FROM ${DatabaseFile.listTable(it.id)} WHERE record_id = ? ORDER BY rowid",
                arrayOf(copy, recordId)
            )
        }
        assignIds(fields, copy)
        copy
    }

    /**
     * The choices List field [field] offers: its items, or the current values of its lookup field.
     * A lookup whose database or field is gone offers nothing.
     */
    suspend fun choicesFor(field: FieldDef): List<String> {
        val props = field.props as? ListProps
        val lookup = props?.lookup
        return when {
            props == null -> emptyList()
            lookup == null -> props.items
            databases.databases.value.orEmpty().none { it.id == lookup.databaseId } -> emptyList()
            else -> databases.distinctValues(lookup.databaseId, lookup.fieldId)
        }
    }

    private fun SQLiteDatabase.assignIds(fields: List<FieldDef>, recordId: Long) {
        fields.filter { it.type == FieldType.ID }.forEach { field ->
            execSQL(
                "UPDATE record SET ${DatabaseFile.columnName(field.id)} = ? WHERE _id = ?",
                arrayOf(nextIdValue(field, databases.idGenerator), recordId)
            )
        }
    }

    private fun SQLiteDatabase.writeChoices(fieldId: Long, recordId: Long, chosen: List<String>) {
        val table = DatabaseFile.listTable(fieldId)
        execSQL("DELETE FROM $table WHERE record_id = ?", arrayOf(recordId))
        chosen.distinct().forEach {
            execSQL("INSERT INTO $table (record_id, value) VALUES (?, ?)", arrayOf(recordId, it))
        }
    }

    /** Records with their values, all of them or only [onlyId]. */
    private fun SQLiteDatabase.readRecords(fields: List<FieldDef>, onlyId: Long?): List<Record> {
        val stored = fields.filter { it.storage != Storage.NONE }
        val columns = stored.joinToString("") { ", ${DatabaseFile.columnName(it.id)}" }
        val where = if (onlyId != null) "WHERE _id = $onlyId" else ""
        val choices = fields.filter { it.isMultiList }.associate { it.id to readChoices(it.id, onlyId) }
        return rawQuery("SELECT _id, _created, _modified$columns FROM record $where ORDER BY _id", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val recordId = c.getLong(0)
                    add(
                        Record(
                            id = recordId,
                            created = c.getString(1),
                            modified = c.getString(2),
                            values = stored.withIndex().associate { (i, f) -> f.id to c.value(i + SYSTEM_COLUMNS) },
                            choices = choices.mapValues { (_, byRecord) -> byRecord[recordId].orEmpty() }
                        )
                    )
                }
            }
        }
    }

    private fun SQLiteDatabase.readChoices(fieldId: Long, onlyId: Long?): Map<Long, List<String>> {
        val where = if (onlyId != null) "WHERE record_id = $onlyId" else ""
        return rawQuery("SELECT record_id, value FROM ${DatabaseFile.listTable(fieldId)} $where ORDER BY rowid", null)
            .use { c -> buildList { while (c.moveToNext()) add(c.getLong(0) to c.getString(1)) } }
            .groupBy({ it.first }, { it.second })
    }

    private fun Cursor.value(index: Int): Any? = when (getType(index)) {
        Cursor.FIELD_TYPE_INTEGER -> getLong(index)
        Cursor.FIELD_TYPE_FLOAT -> getDouble(index)
        Cursor.FIELD_TYPE_NULL -> null
        else -> getString(index)
    }

    private companion object {
        /** `_id`, `_created`, `_modified` come before the field columns. */
        const val SYSTEM_COLUMNS = 3
    }
}
