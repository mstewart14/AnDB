package ca.stewartclan.andb.data

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

data class FieldDef(
    val id: Long,
    val position: Int,
    val name: String,
    val type: FieldType,
    val props: FieldProps = FieldProps.default(type)
)

/**
 * All AnDB databases, one SQLite file each in [dir]. The chooser list is whatever files are in the
 * directory, so there is no separate catalog to fall out of step with them.
 */
class DatabaseRepository(
    private val dir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Instant = Instant::now,
    private val idGenerator: IdGenerator = IdGenerator()
) {
    private val writeLock = Mutex()
    private val _databases = MutableStateFlow<List<DatabaseSummary>?>(null)

    /** Databases sorted by name; `null` until the first [refresh] completes. */
    val databases: StateFlow<List<DatabaseSummary>?> = _databases.asStateFlow()

    suspend fun refresh() = withContext(ioDispatcher) {
        val files = dir.listFiles { f -> f.isFile && f.extension == DatabaseFile.EXTENSION }.orEmpty()
        _databases.value = files.mapNotNull(::readSummary).sortedWith(
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

    suspend fun rename(id: String, name: String) = mutate {
        withDatabase(id) { setMeta(DatabaseFile.META_NAME, name) }
    }

    suspend fun delete(id: String) = mutate {
        check(SQLiteDatabase.deleteDatabase(fileFor(id))) { "No database $id" }
    }

    suspend fun fields(id: String): List<FieldDef> = withContext(ioDispatcher) {
        withDatabase(id) { readFields() }
    }

    /**
     * Appends a field after the existing ones, adding its record column if it has storage. A new
     * ID field gives every existing record an ID.
     */
    suspend fun addField(
        id: String,
        name: String,
        type: FieldType,
        props: FieldProps = FieldProps.default(type)
    ): FieldDef = mutate {
        withDatabase(id) {
            transaction {
                val position = longQuery("SELECT COALESCE(MAX(position) + 1, 0) FROM andb_field").toInt()
                execSQL(
                    "INSERT INTO andb_field (position, name, type, props) VALUES (?, ?, ?, ?)",
                    arrayOf(position, name, type.code, FieldProps.encode(props))
                )
                val field = FieldDef(longQuery("SELECT last_insert_rowid()"), position, name, type, props)
                addColumn(field)
                field
            }
        }
    }

    /**
     * Changes a field's name, type and settings. When the storage class changes, values are
     * converted with SQL `CAST` (so text that isn't a number becomes 0), or dropped when the new
     * type has no storage. Becoming an ID, or changing the kind of ID, regenerates every ID.
     */
    suspend fun updateField(id: String, fieldId: Long, name: String, type: FieldType, props: FieldProps) = mutate {
        withDatabase(id) {
            transaction {
                val fields = readFields()
                val old = fields.first { it.id == fieldId }
                val new = old.copy(name = name, type = type, props = props)
                execSQL(
                    "UPDATE andb_field SET name = ?, type = ?, props = ? WHERE id = ?",
                    arrayOf(name, type.code, FieldProps.encode(props), fieldId)
                )
                when {
                    old.type.storage == type.storage -> Unit
                    old.type.storage == Storage.NONE -> addColumn(new)
                    else -> rebuildRecordTable(
                        recordColumns(fields.map { if (it.id == fieldId) new else it }) { field, column ->
                            when {
                                field.id != fieldId -> column
                                type == FieldType.ID -> "NULL"
                                else -> "CAST($column AS ${type.storage.sqlType})"
                            }
                        }
                    )
                }
                if (type == FieldType.ID) updateIds(old, new)
                if (old.type == FieldType.ID && type != FieldType.ID) deleteMeta(DatabaseFile.nextIdKey(fieldId))
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
                val remaining = fields - field
                writePositions(remaining.map { it.id })
                if (field.type.storage != Storage.NONE) {
                    rebuildRecordTable(recordColumns(remaining))
                }
            }
        }
    }

    /** Adds an empty record, assigning its ID fields, and returns its record id. */
    suspend fun createRecord(id: String): Long = mutate {
        withDatabase(id) {
            transaction {
                val now = clock().toString()
                execSQL("INSERT INTO record (_created, _modified) VALUES (?, ?)", arrayOf(now, now))
                val recordId = longQuery("SELECT last_insert_rowid()")
                readFields().filter { it.type == FieldType.ID }.forEach { field ->
                    execSQL(
                        "UPDATE record SET ${DatabaseFile.columnName(field.id)} = ? WHERE _id = ?",
                        arrayOf(nextIdValue(field, idGenerator), recordId)
                    )
                }
                recordId
            }
        }
    }

    /** Adds the record column for a new or newly stored [field]. */
    private fun SQLiteDatabase.addColumn(field: FieldDef) {
        val sqlType = field.type.storage.sqlType ?: return
        val column = DatabaseFile.columnName(field.id)
        execSQL("ALTER TABLE record ADD COLUMN $column $sqlType")
        if (field.type == FieldType.ID) {
            createUniqueIndex(column)
            backfillIds(field, idGenerator)
        }
    }

    /**
     * Brings ID field [new] up to date after an update from [old]. A field that just became an ID
     * already has empty values (its column was added or rebuilt), so only a kind change clears them.
     */
    private fun SQLiteDatabase.updateIds(old: FieldDef, new: FieldDef) {
        val props = new.props as IdProps
        val oldProps = old.props as? IdProps
        val column = DatabaseFile.columnName(new.id)
        val key = DatabaseFile.nextIdKey(new.id)
        if (oldProps != null && oldProps.kind != props.kind) {
            execSQL("UPDATE record SET $column = NULL")
            deleteMeta(key)
        } else if (oldProps != null && props.start != oldProps.start) {
            // Never move the counter back: numbers already handed out are not reused.
            getMeta(key)?.toLong()?.let { next -> setMeta(key, maxOf(next, props.start).toString()) }
        }
        createUniqueIndex(column)
        backfillIds(new, idGenerator)
    }

    private fun fileFor(id: String): File {
        require(UUID.fromString(id).toString() == id) { "Bad database id: $id" }
        return File(dir, "$id.${DatabaseFile.EXTENSION}")
    }

    private fun <T> withDatabase(id: String, block: SQLiteDatabase.() -> T): T =
        DatabaseFile.open(fileFor(id)).use(block)

    /** Runs a change off the main thread, one at a time, then refreshes [databases]. */
    private suspend fun <T> mutate(block: () -> T): T = writeLock.withLock {
        withContext(ioDispatcher) { block() }.also { refresh() }
    }

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
