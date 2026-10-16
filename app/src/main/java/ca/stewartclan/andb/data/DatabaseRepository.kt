package ca.stewartclan.andb.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.core.database.sqlite.transaction
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A database as shown in the chooser. [id] is the file's base name and never changes. */
data class DatabaseSummary(val id: String, val name: String, val recordCount: Long, val fieldCount: Long)

/**
 * All AnDB databases, one SQLite file each in [dir]. The chooser list is whatever files are in the
 * directory, so there is no separate catalog to fall out of step with them.
 */
class DatabaseRepository(
    internal val dir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    internal val clock: () -> Instant = Instant::now,
    internal val idGenerator: IdGenerator = IdGenerator()
) {
    private val writeLock = Mutex()
    private val _databases = MutableStateFlow<List<DatabaseSummary>?>(null)

    /** Databases sorted by name; `null` until the first [refresh] completes. */
    val databases: StateFlow<List<DatabaseSummary>?> = _databases.asStateFlow()

    suspend fun refresh() = withContext(ioDispatcher) {
        _databases.value = readSummaries().sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER, DatabaseSummary::name).thenBy(DatabaseSummary::id)
        )
    }

    /** Creates an empty database and returns its id. */
    suspend fun create(name: String): String = mutate {
        dir.mkdirs()
        val id = UUID.randomUUID().toString()
        DatabaseFile.create(fileFor(id), name, clock().toString()).close()
        id
    }

    /**
     * Copies database [id] as a new database named [name] and returns its id: its design, list
     * views and filters, and with [withRecords] its records. Links and List lookups to the database
     * itself point into the copy instead. Without records, ID counters start over.
     */
    suspend fun duplicate(id: String, name: String, withRecords: Boolean): String = mutate {
        val copyId = UUID.randomUUID().toString()
        val copy = fileFor(copyId)
        // Every change goes through this lock and closes its connection, so the file is at rest.
        fileFor(id).copyTo(copy)
        try {
            withDatabase(copyId) {
                transaction {
                    setMeta(DatabaseFile.META_NAME, name)
                    setMeta(DatabaseFile.META_CREATED, clock().toString())
                    pointSelfReferencesAt(id, copyId, withRecords)
                    if (!withRecords) clearRecords()
                }
            }
        } catch (e: SQLiteException) {
            copy.delete()
            throw e
        }
        copyId
    }

    suspend fun rename(id: String, name: String) = mutate {
        withDatabase(id) { setMeta(DatabaseFile.META_NAME, name) }
    }

    /**
     * Deletes database [id]. Link fields elsewhere that point at it remember its name, so a new
     * database of that name can take its place.
     */
    suspend fun delete(id: String) = mutate {
        val name = withDatabase(id) { getMeta(DatabaseFile.META_NAME) }
        if (name != null) {
            readSummaries().filter { it.id != id }.forEach { other ->
                try {
                    withDatabase(other.id) { nameDeletedDatabase(id, name) }
                } catch (e: SQLiteException) {
                    // One unwritable database mustn't stop the delete; its links just won't know the name.
                    Log.w(TAG, "Couldn't note deleted database in ${other.id}", e)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Skipping ${other.id}: ${e.message}")
                }
            }
        }
        check(SQLiteDatabase.deleteDatabase(fileFor(id))) { "No database $id" }
    }

    suspend fun fields(id: String): List<FieldDef> = withContext(ioDispatcher) {
        withDatabase(id) { readFields() }
    }

    /**
     * The saved title of each link in Link field [fieldId] of database [id], by record id; for a
     * field allowing several links, by link (its row in the field's link list table).
     */
    suspend fun linkTitles(id: String, fieldId: Long): Map<Long, String> = withContext(ioDispatcher) {
        withDatabase(id) {
            val sql = if (readFields().find { it.id == fieldId }?.isMultiLink == true) {
                "SELECT rowid, name FROM ${DatabaseFile.linkListTable(fieldId)}"
            } else {
                "SELECT l.record_id, l.name FROM ${DatabaseFile.linkTable(fieldId)} l " +
                    "JOIN record r ON r._id = l.record_id WHERE r.${DatabaseFile.columnName(fieldId)} IS NOT NULL"
            }
            rawQuery(sql, null).use { c -> buildMap { while (c.moveToNext()) put(c.getLong(0), c.getString(1)) } }
        }
    }

    /**
     * Appends a field after the existing ones, adding its record column if it has storage. A new
     * ID field gives every existing record an ID.
     */
    suspend fun addField(
        id: String,
        name: String,
        type: FieldType,
        props: FieldProps = FieldProps.default(type),
        required: Boolean = false
    ): FieldDef = mutate {
        withDatabase(id) {
            transaction {
                val position = longQuery("SELECT COALESCE(MAX(position) + 1, 0) FROM andb_field").toInt()
                val isRequired = required && type.canBeRequired
                execSQL(
                    "INSERT INTO andb_field (position, name, type, props, required) VALUES (?, ?, ?, ?, ?)",
                    arrayOf(position, name, type.code, FieldProps.encode(props), if (isRequired) 1 else 0)
                )
                val field = FieldDef(longQuery("SELECT last_insert_rowid()"), position, name, type, props, isRequired)
                addColumn(field, idGenerator)
                field
            }
        }
    }

    /**
     * Changes field [FieldDef.id]'s name, type, settings and required flag to [field]'s (its
     * position is ignored; see [moveField]). When the storage class changes, values are
     * converted with SQL `CAST` (so text that isn't a number becomes 0), or dropped when the new
     * type has no storage. Becoming an ID, or changing the kind of ID, regenerates every ID. A List
     * switched to multiple choices keeps each value as a choice; switched back, it keeps the first;
     * likewise a Link to records allowing several links. A Link field to records pointed at another
     * database carries over what [repoint] says.
     */
    suspend fun updateField(id: String, field: FieldDef, repoint: Repoint? = null) = mutate {
        withDatabase(id) {
            transaction {
                val fields = readFields()
                val old = fields.first { it.id == field.id }
                val new = field.copy(position = old.position, required = field.required && field.type.canBeRequired)
                val type = new.type
                execSQL(
                    "UPDATE andb_field SET name = ?, type = ?, props = ?, required = ? WHERE id = ?",
                    arrayOf(new.name, type.code, FieldProps.encode(new.props), if (new.required) 1 else 0, new.id)
                )
                reshapeField(fields, old, new, repoint?.links, idGenerator)
                repoint?.linkedTargets?.forEach { (linkedId, targetId) ->
                    val linked = fields.find { it.id == linkedId }?.props as? LinkedProps ?: return@forEach
                    execSQL(
                        "UPDATE andb_field SET props = ? WHERE id = ?",
                        arrayOf(FieldProps.encode(linked.copy(targetFieldId = targetId)), linkedId)
                    )
                }
                if (type == FieldType.ID) updateIds(old, new, idGenerator)
                if (old.type == FieldType.ID && type != FieldType.ID) deleteMeta(DatabaseFile.nextIdKey(new.id))
            }
        }
    }

    /** Moves a field to [toIndex] in display order, shifting the fields in between. */
    suspend fun moveField(id: String, fieldId: Long, toIndex: Int) = mutate {
        withDatabase(id) {
            transaction {
                val ids = readFields().map { it.id }.toMutableList()
                require(ids.remove(fieldId)) { "No field $fieldId" }
                ids.add(toIndex.coerceIn(0, ids.size), fieldId)
                writePositions(ids)
            }
        }
    }

    /** Deletes a field and, if it has storage, its values in every record. */
    suspend fun deleteField(id: String, fieldId: Long) = mutate {
        withDatabase(id) {
            transaction {
                val fields = readFields()
                val field = fields.first { it.id == fieldId }
                execSQL("DELETE FROM andb_field WHERE id = ?", arrayOf(fieldId))
                deleteMeta(DatabaseFile.nextIdKey(fieldId))
                execSQL("DROP TABLE IF EXISTS ${DatabaseFile.listTable(fieldId)}")
                execSQL("DROP TABLE IF EXISTS ${DatabaseFile.linkTable(fieldId)}")
                execSQL("DROP TABLE IF EXISTS ${DatabaseFile.linkListTable(fieldId)}")
                val remaining = fields - field
                writePositions(remaining.map { it.id })
                if (field.storage != Storage.NONE) {
                    rebuildRecordTable(recordColumns(remaining))
                }
            }
        }
    }

    /**
     * The distinct values field [fieldId] currently holds across all records, sorted, as text (IDs
     * formatted). This is what a List looking up that field offers. Empty if the field is gone.
     */
    suspend fun distinctValues(id: String, fieldId: Long): List<String> = withContext(ioDispatcher) {
        withDatabase(id) {
            val field = readFields().firstOrNull { it.id == fieldId }
            val column = DatabaseFile.columnName(fieldId)
            val sql = when {
                field == null -> null
                field.isMultiList -> "SELECT DISTINCT value FROM ${DatabaseFile.listTable(fieldId)}"
                field.isMultiLink -> "SELECT DISTINCT name FROM ${DatabaseFile.linkListTable(fieldId)}"
                field.type == FieldType.LINK -> "SELECT DISTINCT name FROM ${DatabaseFile.linkTable(fieldId)}"
                field.storage == Storage.NONE -> null
                else -> "SELECT DISTINCT $column FROM record WHERE $column IS NOT NULL AND $column != ''"
            }
            val idProps = field?.props as? IdProps
            sql?.let {
                rawQuery(it, null).use { c ->
                    buildList {
                        while (c.moveToNext()) {
                            val isNumber = c.getType(0) == Cursor.FIELD_TYPE_INTEGER
                            add(if (idProps != null && isNumber) idProps.format(c.getLong(0)) else c.getString(0))
                        }
                    }
                }
            }.orEmpty().sortedWith(String.CASE_INSENSITIVE_ORDER)
        }
    }

    /** Reads database [id] off the main thread. For [RecordRepository]. */
    internal suspend fun <T> read(id: String, block: SQLiteDatabase.() -> T): T =
        withContext(ioDispatcher) { withDatabase(id, block) }

    /** Changes database [id] in one transaction, serialised with all other changes. For [RecordRepository]. */
    internal suspend fun <T> write(id: String, block: SQLiteDatabase.() -> T): T = mutate {
        withDatabase(id) { transaction { block() } }
    }

    internal fun fileFor(id: String): File {
        require(UUID.fromString(id).toString() == id) { "Bad database id: $id" }
        return File(dir, "$id.${DatabaseFile.EXTENSION}")
    }

    private fun <T> withDatabase(id: String, block: SQLiteDatabase.() -> T): T =
        DatabaseFile.open(fileFor(id)).use(block)

    /** Runs a change off the main thread, one at a time, then refreshes [databases]. */
    internal suspend fun <T> mutate(block: () -> T): T = writeLock.withLock {
        withContext(ioDispatcher) { block() }.also { refresh() }
    }

    /** Every database file's summary, read from disk; unreadable files are left out. */
    internal fun readSummaries(): List<DatabaseSummary> =
        dir.listFiles { f -> f.isFile && f.extension == DatabaseFile.EXTENSION }.orEmpty().mapNotNull(::readSummary)

    private fun readSummary(file: File): DatabaseSummary? = try {
        DatabaseFile.open(file).use { db ->
            DatabaseSummary(
                id = file.nameWithoutExtension,
                name = db.getMeta(DatabaseFile.META_NAME) ?: file.nameWithoutExtension,
                recordCount = db.longQuery("SELECT COUNT(*) FROM record"),
                fieldCount = db.longQuery("SELECT COUNT(*) FROM andb_field")
            )
        }
    } catch (e: SQLiteException) {
        Log.w(TAG, "Skipping unreadable database ${file.name}", e)
        null
    } catch (e: IllegalStateException) {
        Log.w(TAG, "Skipping ${file.name}: ${e.message}")
        null
    }

    private companion object {
        const val TAG = "DatabaseRepository"
    }
}
