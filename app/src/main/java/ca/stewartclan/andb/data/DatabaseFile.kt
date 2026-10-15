package ca.stewartclan.andb.data

import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import java.io.File

/**
 * On-disk layout of one AnDB database: a single SQLite file holding the user's records plus the
 * metadata describing them.
 *
 * - `andb_meta`: key/value properties of the database (display name, creation time).
 * - `andb_field`: one row per user-defined field, in display order.
 * - `record`: one row per record. Each field with storage gets a column named `f<field id>`, so
 *   renaming a field never touches the record table.
 */
internal object DatabaseFile {
    const val EXTENSION = "db"

    /** `PRAGMA application_id`, ASCII "AnDB", marks a SQLite file as ours. */
    const val APPLICATION_ID = 0x416E4442

    const val SCHEMA_VERSION = 1

    const val META_NAME = "name"
    const val META_CREATED = "created"

    fun columnName(fieldId: Long) = "f$fieldId"

    fun open(file: File): SQLiteDatabase {
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        check(db.applicationId() == APPLICATION_ID) { "Not an AnDB database: ${file.name}" }
        check(db.version <= SCHEMA_VERSION) { "${file.name} was made by a newer AnDB (schema ${db.version})" }
        return db
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
            execSQL(
                "CREATE TABLE record (_id INTEGER PRIMARY KEY, _created TEXT NOT NULL, _modified TEXT NOT NULL)"
            )
            setMeta(META_NAME, name)
            setMeta(META_CREATED, created)
            version = SCHEMA_VERSION
        }
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
