package ca.stewartclan.andb.data

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException

/** Records of AnDB databases. Changes go through [databases] so they're serialised with design changes. */
class RecordRepository(private val databases: DatabaseRepository) {

    /** All records of database [id], sorted by [sort] (oldest first by default). */
    suspend fun records(id: String, sort: List<SortKey> = emptyList()): List<Record> = databases.read(id) {
        val fields = readFields()
        readRecords(fields, null, orderBy(fields, sort))
    }

    /** Ids of all records of database [id], oldest first. */
    suspend fun recordIds(id: String): List<Long> = databases.read(id) {
        rawQuery("SELECT _id FROM record ORDER BY _id", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0)) }
        }
    }

    suspend fun record(id: String, recordId: Long): Record? =
        databases.read(id) { readRecords(readFields(), recordId).firstOrNull() }

    /**
     * Adds a record with [values] and [choices] (as in [save]), assigns its IDs and returns its
     * record id. Refused if a required field would be empty.
     */
    suspend fun insert(id: String, values: Map<Long, Any?>, choices: Map<Long, List<String>>): Long =
        databases.write(id) {
            val fields = readFields()
            checkRequired(fields, values, choices)
            val now = databases.clock().toString()
            execSQL("INSERT INTO record (_created, _modified) VALUES (?, ?)", arrayOf(now, now))
            val recordId = longQuery("SELECT last_insert_rowid()")
            writeValues(fields, recordId, values, choices, now)
            assignIds(fields, recordId)
            recordId
        }

    /**
     * Adds [rows] as new records in one go (as from a CSV file), skipping rows that can't be
     * stored: a required field empty, or (with [keepIds]) an ID already in use. With [keepIds], ID
     * values given in a row are kept and increment counters move past them; otherwise every
     * record gets new IDs.
     */
    suspend fun insertAll(id: String, rows: List<ImportRow>, keepIds: Boolean): ImportOutcome = databases.write(id) {
        val fields = readFields()
        val now = databases.clock().toString()
        val skipped = mutableMapOf<Int, RowProblem>()
        rows.forEachIndexed { index, row ->
            if (RequiredRule.missing(fields, row.values, row.choices).isNotEmpty()) {
                skipped[index] = RowProblem.REQUIRED_MISSING
                return@forEachIndexed
            }
            // A savepoint per row: a row that fails is undone without losing the others.
            execSQL("SAVEPOINT import_row")
            try {
                execSQL("INSERT INTO record (_created, _modified) VALUES (?, ?)", arrayOf(now, now))
                val recordId = longQuery("SELECT last_insert_rowid()")
                writeValues(fields, recordId, row.values, row.choices, now)
                assignIds(fields, recordId, if (keepIds) row.values else emptyMap())
                execSQL("RELEASE import_row")
            } catch (_: SQLiteConstraintException) {
                execSQL("ROLLBACK TO import_row")
                execSQL("RELEASE import_row")
                skipped[index] = RowProblem.DUPLICATE_ID
            }
        }
        ImportOutcome(rows.size - skipped.size, skipped)
    }

    /** Adds an empty record; only possible while no field is required. */
    suspend fun create(id: String): Long = insert(id, emptyMap(), emptyMap())

    /**
     * Saves the given values and choices of record [recordId] and updates its modified time. Fields
     * not given, ID fields and fields that no longer hold values are left alone. Refused if a
     * required field would be empty afterwards.
     */
    suspend fun save(id: String, recordId: Long, values: Map<Long, Any?>, choices: Map<Long, List<String>>) =
        databases.write(id) {
            val fields = readFields()
            val stored = readRecords(fields, recordId).single()
            checkRequired(fields, stored.values + values, stored.choices + choices)
            writeValues(fields, recordId, values, choices, databases.clock().toString())
        }

    suspend fun delete(id: String, recordId: Long) = databases.write(id) {
        val fields = readFields()
        fields.filter { it.isMultiList }.forEach {
            execSQL("DELETE FROM ${DatabaseFile.listTable(it.id)} WHERE record_id = ?", arrayOf(recordId))
        }
        fields.filter { it.type == FieldType.LINK }.forEach {
            execSQL("DELETE FROM ${DatabaseFile.linkTable(it.id)} WHERE record_id = ?", arrayOf(recordId))
        }
        val photos = storedPhotos(recordId)
        execSQL("DELETE FROM record WHERE _id = ?", arrayOf(recordId))
        photos.forEach { deleteUnusedPhoto(it) }
    }

    /** The JPEG of photo [uri] stored in database [id], or null if it isn't there. */
    suspend fun storedPhoto(id: String, uri: String): ByteArray? =
        StoredPhoto.id(uri)?.let { photoId -> databases.read(id) { readStoredPhoto(photoId) } }

    /**
     * Sets Link field [fieldId] of each record in [links] (by record id) without counting as an
     * edit (the modified time stays), as when a field starts copying its photos into the database.
     */
    suspend fun replaceLinks(id: String, fieldId: Long, links: Map<Long, LinkValue>) = databases.write(id) {
        links.forEach { (recordId, link) -> writeLink(fieldId, recordId, link) }
    }

    /**
     * Copies record [recordId] (new IDs and timestamps) and returns the copy's record id. Refused
     * if the record lacks a required value, as the copy would too.
     */
    suspend fun duplicate(id: String, recordId: Long): Long = databases.write(id) {
        val fields = readFields()
        val source = readRecords(fields, recordId).single()
        checkRequired(fields, source.values, source.choices)
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
        fields.filter { it.type == FieldType.LINK }.forEach {
            execSQL(
                "INSERT INTO ${DatabaseFile.linkTable(it.id)} (record_id, name, thumbnail) " +
                    "SELECT ?, name, thumbnail FROM ${DatabaseFile.linkTable(it.id)} WHERE record_id = ?",
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

    /**
     * The records linking to record [recordId] of database [id], grouped by database and Link field;
     * every Link field into database [id] has a group, empty if nothing links here through it yet.
     * Databases that can't be read are skipped.
     */
    suspend fun backlinks(id: String, recordId: Long): List<Backlinks> {
        if (databases.databases.value == null) databases.refresh()
        val uri = RecordRef(id, recordId).uri
        return databases.databases.value.orEmpty().flatMap { db ->
            try {
                databases.read(db.id) {
                    readFields().filter { it.recordLink?.databaseId == id }.map { field ->
                        val column = DatabaseFile.columnName(field.id)
                        val ids = rawQuery("SELECT _id FROM record WHERE $column = ? ORDER BY _id", arrayOf(uri))
                            .use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }
                        Backlinks(db.id, db.name, field, ids)
                    }
                }
            } catch (_: SQLiteException) {
                emptyList()
            } catch (_: IllegalStateException) {
                emptyList()
            }
        }
    }

    private fun checkRequired(fields: List<FieldDef>, values: Map<Long, Any?>, choices: Map<Long, List<String>>) {
        val missing = RequiredRule.missing(fields, values, choices)
        if (missing.isNotEmpty()) throw RequiredFieldsMissingException(missing.map { it.name })
    }

    /**
     * Gives record [recordId] its IDs: the ones in [kept] where given (an increment's counter then
     * moves past it), new ones otherwise.
     */
    private fun SQLiteDatabase.assignIds(fields: List<FieldDef>, recordId: Long, kept: Map<Long, Any?> = emptyMap()) {
        fields.filter { it.type == FieldType.ID }.forEach { field ->
            val given = kept[field.id]
            if (given is Long) keepIncrementCounterPast(field, given)
            execSQL(
                "UPDATE record SET ${DatabaseFile.columnName(field.id)} = ? WHERE _id = ?",
                arrayOf(given ?: nextIdValue(field, databases.idGenerator), recordId)
            )
        }
    }

    private fun SQLiteDatabase.keepIncrementCounterPast(field: FieldDef, value: Long) {
        val props = field.props as? IdProps ?: return
        val key = DatabaseFile.nextIdKey(field.id)
        val next = getMeta(key)?.toLong() ?: props.start
        if (value >= next) setMeta(key, (value + 1).toString())
    }
}
