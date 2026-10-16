package ca.stewartclan.andb.data

import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import java.io.File

/**
 * On-disk layout of one AnDB database: a single SQLite file holding the user's records plus the
 * metadata describing them.
 *
 * - `andb_meta`: key/value properties of the database (display name, creation time, ID counters).
 * - `andb_field`: one row per user-defined field, in display order, with its [FieldProps] as JSON.
 * - `record`: one row per record. Each field with storage gets a column named `f<field id>`, so
 *   renaming a field never touches the record table. ID columns have a unique index `u_f<field id>`.
 * - `list_f<field id>`: the choices of a multiple-choice List field, one row per record and choice.
 * - `link_f<field id>`: the snapshot (name, thumbnail) of each record's link in a Link field; the
 *   link's uri is in the record column.
 * - `andb_view`: named list views, with their columns and sort keys as JSON.
 * - `andb_filter`: saved filters, with their conditions as JSON.
 */
internal object DatabaseFile {
    const val EXTENSION = "db"

    /** `PRAGMA application_id`, ASCII "AnDB", marks a SQLite file as ours. */
    const val APPLICATION_ID = 0x416E4442

    /**
     * Upgrades, in order: entry `i` takes a file from schema version `i + 1` to `i + 2`. Files are
     * created at version 1 and brought up to date by these too, so there is one path for both.
     */
    private val migrations: List<SQLiteDatabase.() -> Unit> = listOf(
        // 2: fields can be required.
        { execSQL("ALTER TABLE andb_field ADD COLUMN required INTEGER NOT NULL DEFAULT 0") },
        // 3: named list views.
        {
            execSQL(
                "CREATE TABLE andb_view (id INTEGER PRIMARY KEY, position INTEGER NOT NULL, name TEXT NOT NULL, " +
                    "columns TEXT NOT NULL DEFAULT '[]', sort TEXT NOT NULL DEFAULT '[]')"
            )
        },
        // 4: saved filters.
        {
            execSQL(
                "CREATE TABLE andb_filter (id INTEGER PRIMARY KEY, position INTEGER NOT NULL, name TEXT NOT NULL, " +
                    "match_all INTEGER NOT NULL DEFAULT 1, conditions TEXT NOT NULL DEFAULT '[]')"
            )
        },
        // 5: record ids are never reused (AUTOINCREMENT), as records can be linked to.
        { rebuildRecordTable(recordColumns(readFields())) },
        // 6: photos copied into the database.
        { execSQL("CREATE TABLE andb_photo (id INTEGER PRIMARY KEY AUTOINCREMENT, data BLOB NOT NULL)") }
    )

    val SCHEMA_VERSION = migrations.size + 1

    const val META_NAME = "name"
    const val META_CREATED = "created"

    /** `andb_meta` key of the list view last shown. */
    const val META_CURRENT_VIEW = "current_view"

    /** `andb_meta` key of the filter applied to the list, if any. */
    const val META_CURRENT_FILTER = "current_filter"

    /** Columns every record table starts with; field columns follow. */
    const val SYSTEM_COLUMNS = "_id, _created, _modified"

    /** `AUTOINCREMENT`: record ids are never reused, so a link can't come to mean another record. */
    const val SYSTEM_COLUMNS_DDL =
        "_id INTEGER PRIMARY KEY AUTOINCREMENT, _created TEXT NOT NULL, _modified TEXT NOT NULL"

    fun columnName(fieldId: Long) = "f$fieldId"

    fun listTable(fieldId: Long) = "list_f$fieldId"

    fun linkTable(fieldId: Long) = "link_f$fieldId"

    /** `andb_meta` key holding the next value of an increment ID field. */
    fun nextIdKey(fieldId: Long) = "next_id.$fieldId"

    fun open(file: File): SQLiteDatabase {
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        check(db.applicationId() == APPLICATION_ID) { "Not an AnDB database: ${file.name}" }
        check(db.version <= SCHEMA_VERSION) { "${file.name} was made by a newer AnDB (schema ${db.version})" }
        migrate(db)
        return db
    }

    private fun migrate(db: SQLiteDatabase) {
        if (db.version == SCHEMA_VERSION) return
        db.transaction {
            for (from in version until SCHEMA_VERSION) migrations[from - 1]()
            version = SCHEMA_VERSION
        }
    }

    fun create(file: File, name: String, created: String): SQLiteDatabase {
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.CREATE_IF_NECESSARY)
        db.transaction {
            execSQL("PRAGMA application_id = $APPLICATION_ID")
            execSQL("CREATE TABLE andb_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            execSQL(
                """
                CREATE TABLE andb_field (
                    id INTEGER PRIMARY KEY,
                    position INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    props TEXT NOT NULL DEFAULT '{}'
                )
                """.trimIndent()
            )
            execSQL("CREATE TABLE record ($SYSTEM_COLUMNS_DDL)")
            setMeta(META_NAME, name)
            setMeta(META_CREATED, created)
            version = 1
        }
        migrate(db)
        return db
    }

    private fun SQLiteDatabase.applicationId(): Int = rawQuery("PRAGMA application_id", null).use {
        if (it.moveToFirst()) it.getInt(0) else 0
    }
}

internal fun SQLiteDatabase.getMeta(key: String): String? =
    rawQuery("SELECT value FROM andb_meta WHERE key = ?", arrayOf(key)).use {
        if (it.moveToFirst()) it.getString(0) else null
    }

internal fun SQLiteDatabase.setMeta(key: String, value: String) {
    execSQL("INSERT OR REPLACE INTO andb_meta (key, value) VALUES (?, ?)", arrayOf(key, value))
}

internal fun SQLiteDatabase.longQuery(sql: String, vararg args: String): Long =
    rawQuery(sql, args).use { if (it.moveToFirst()) it.getLong(0) else 0 }

/**
 * A field column of the rebuilt record table, filled from [source], an SQL expression over the old
 * table. [unique] columns get a unique index.
 */
internal data class RecordColumn(val name: String, val sqlType: String, val source: String, val unique: Boolean)

/**
 * Recreates `record` with the system columns plus [columns], copying every row. Needed to drop or
 * retype a column: Android's SQLite only gained `DROP COLUMN` in Android 14. Call inside a transaction.
 */
internal fun SQLiteDatabase.rebuildRecordTable(columns: List<RecordColumn>) {
    val ddl = columns.joinToString("") { ", ${it.name} ${it.sqlType}" }
    val names = columns.joinToString("") { ", ${it.name}" }
    val sources = columns.joinToString("") { ", ${it.source}" }
    execSQL("CREATE TABLE record_new (${DatabaseFile.SYSTEM_COLUMNS_DDL}$ddl)")
    execSQL(
        "INSERT INTO record_new (${DatabaseFile.SYSTEM_COLUMNS}$names) " +
            "SELECT ${DatabaseFile.SYSTEM_COLUMNS}$sources FROM record"
    )
    execSQL("DROP TABLE record")
    execSQL("ALTER TABLE record_new RENAME TO record")
    columns.filter { it.unique }.forEach { createUniqueIndex(it.name) }
}

internal fun SQLiteDatabase.deleteMeta(key: String) {
    execSQL("DELETE FROM andb_meta WHERE key = ?", arrayOf(key))
}

internal fun SQLiteDatabase.readFields(): List<FieldDef> =
    rawQuery("SELECT id, position, name, type, props, required FROM andb_field ORDER BY position", null).use { c ->
        val id = c.getColumnIndexOrThrow("id")
        val position = c.getColumnIndexOrThrow("position")
        val name = c.getColumnIndexOrThrow("name")
        val type = c.getColumnIndexOrThrow("type")
        val props = c.getColumnIndexOrThrow("props")
        val required = c.getColumnIndexOrThrow("required")
        buildList {
            while (c.moveToNext()) {
                val fieldType = FieldType.fromCode(c.getString(type))
                add(
                    FieldDef(
                        c.getLong(id),
                        c.getInt(position),
                        c.getString(name),
                        fieldType,
                        FieldProps.decode(fieldType, c.getString(props)),
                        c.getInt(required) != 0
                    )
                )
            }
        }
    }

/** Record columns for [fields] that have storage; [source] gives each one's value from the old table. */
internal fun recordColumns(
    fields: List<FieldDef>,
    source: (FieldDef, String) -> String = { _, name -> name }
): List<RecordColumn> = fields.mapNotNull { field ->
    field.storage.sqlType?.let { sqlType ->
        val name = DatabaseFile.columnName(field.id)
        RecordColumn(name, sqlType, source(field, name), unique = field.type == FieldType.ID)
    }
}

internal fun SQLiteDatabase.writePositions(fieldIds: List<Long>) {
    fieldIds.forEachIndexed { position, fieldId ->
        execSQL("UPDATE andb_field SET position = ? WHERE id = ?", arrayOf(position, fieldId))
    }
}
