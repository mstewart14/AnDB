package ca.stewartclan.andb.data

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.core.database.sqlite.transaction
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Databases as files to keep or share: an `.andb` file is the database's own SQLite file, with or
 * without its records. Links to other databases carry those databases' names, so on import a
 * database of the same name takes their place (see [LinkProps.missingName]).
 */
class DatabaseTransfer(private val repository: DatabaseRepository) {

    /** Database [id] as an `.andb` file's bytes: its design, and its records if [withRecords]. */
    suspend fun export(id: String, withRecords: Boolean): ByteArray = repository.mutate {
        val names = repository.readSummaries().associate { it.id to it.name }
        withTempFile { temp ->
            repository.fileFor(id).copyTo(temp, overwrite = true)
            DatabaseFile.open(temp).use { db ->
                db.transaction {
                    setMeta(META_SOURCE_ID, id)
                    if (!withRecords) clearRecords()
                    nameLinkedDatabases(id, names)
                }
            }
            temp.readBytes()
        }
    }

    /** Adds the database in `.andb` file [bytes], as [import] from a stream. */
    suspend fun import(bytes: ByteArray): String = import(bytes.inputStream())

    /**
     * Adds the database in `.andb` file [source] (closed once read) and returns its id. It keeps its
     * name, numbered if taken ("Wines (2)"); links and lookups to itself point at the new database.
     * Files come from other people, so one is refused, adding nothing, unless it's a sound AnDB
     * database no newer than this AnDB, no larger than [MAX_IMPORT_BYTES], holding only what AnDB
     * puts there (no triggers, which would run with later edits) and field settings it can read.
     */
    suspend fun import(source: InputStream): String {
        val temp = withContext(Dispatchers.IO) {
            repository.dir.mkdirs()
            File.createTempFile("transfer", ".tmp", repository.dir)
        }
        try {
            // Copied outside the write lock: a slow source mustn't hold up other changes.
            withContext(Dispatchers.IO) {
                source.use { input ->
                    temp.outputStream().use {
                        input.copyAtMost(it, MAX_IMPORT_BYTES) { IllegalStateException(TOO_LARGE) }
                    }
                }
            }
            return repository.mutate { add(temp) }
        } finally {
            temp.delete()
            File(temp.path + "-journal").delete()
        }
    }

    /** Adds imported file [temp] as a new database (see [import]) and returns its id. */
    private fun add(temp: File): String {
        // Most often a CSV file chosen here instead of in a database's menu: say where it goes.
        check(!isText(temp)) { TEXT_FILE }
        val taken = repository.readSummaries().map { it.name }
        val id = UUID.randomUUID().toString()
        // Whatever is wrong with the file, the message would only name the scratch file.
        val failure = try {
            checkImportable(temp)
            DatabaseFile.open(temp).use { db -> db.transaction { adopt(id, taken) } }
            null
        } catch (e: SQLiteException) {
            e
        } catch (e: IllegalStateException) {
            // Not ours, or made by a newer AnDB.
            e
        } catch (e: IllegalArgumentException) {
            // Field settings that can't be read.
            e
        }
        if (failure != null) throw IllegalStateException(NOT_ANDB, failure)
        check(temp.renameTo(repository.fileFor(id))) { "Couldn't add the database" }
        return id
    }

    /** Whether [file] is text (such as CSV) rather than a database: no SQLite header, no zero bytes. */
    private fun isText(file: File): Boolean {
        val start = ByteArray(TEXT_SNIFF_BYTES)
        val read = file.inputStream().use { it.read(start) }
        if (read <= 0) return false
        val head = start.copyOf(read)
        return !head.decodeToString().startsWith(SQLITE_HEADER) && head.none { it == 0.toByte() }
    }

    /**
     * Refuses [file] unless it's a sound SQLite file holding only the tables and indexes AnDB makes:
     * a trigger or view could change the database whenever it's used.
     */
    private fun checkImportable(file: File) {
        val flags = SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        SQLiteDatabase.openDatabase(file.path, null, flags).use { db ->
            val sound = db.rawQuery("PRAGMA quick_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }
            check(sound) { "Damaged database" }
            db.rawQuery("SELECT type, name FROM sqlite_master", null).use { c ->
                while (c.moveToNext()) {
                    val type = c.getString(0)
                    val name = c.getString(1)
                    check(EXPECTED[type]?.matches(name) == true) { "Unexpected $type $name" }
                }
            }
        }
    }

    /** Makes this imported database database [id], under a name not among [taken]. */
    private fun SQLiteDatabase.adopt(id: String, taken: List<String>) {
        // Field settings it can't read refuse the file now, rather than each time it's opened.
        readFields()
        val base = getMeta(DatabaseFile.META_NAME)?.trim()?.takeIf { it.isNotEmpty() } ?: IMPORTED
        val name = (sequenceOf(base) + generateSequence(2) { it + 1 }.map { "$base ($it)" })
            .first { candidate -> taken.none { it.equals(candidate, ignoreCase = true) } }
        setMeta(DatabaseFile.META_NAME, name)
        setMeta(DatabaseFile.META_CREATED, repository.clock().toString())
        getMeta(META_SOURCE_ID)?.let { pointSelfReferencesAt(it, id, withRecords = true) }
        deleteMeta(META_SOURCE_ID)
    }

    /** Has this exported copy of database [self] name the other databases its Link fields point at. */
    private fun SQLiteDatabase.nameLinkedDatabases(self: String, names: Map<String, String>) {
        readFields().forEach { field ->
            val link = field.recordLink ?: return@forEach
            val name = link.databaseId?.takeIf { it != self }?.let { names[it] } ?: return@forEach
            execSQL(
                "UPDATE andb_field SET props = ? WHERE id = ?",
                arrayOf(FieldProps.encode(link.copy(missingName = name)), field.id)
            )
        }
    }

    /** Runs [block] with a scratch file beside the databases (not one, so the chooser ignores it). */
    private fun <T> withTempFile(block: (File) -> T): T {
        repository.dir.mkdirs()
        val temp = File.createTempFile("transfer", ".tmp", repository.dir)
        return try {
            block(temp)
        } finally {
            temp.delete()
            File(temp.path + "-journal").delete()
        }
    }

    private companion object {
        /** `andb_meta` key, in exported files only, of the database they came from. */
        const val META_SOURCE_ID = "exported_from"

        /** Name for an imported database that somehow has none. */
        const val IMPORTED = "Imported"

        const val NOT_ANDB = "That file isn't an AnDB database, or it's from a newer version of AnDB"

        /** Largest `.andb` file imported: far beyond any real database, short of filling the phone. */
        const val MAX_IMPORT_BYTES = 512L * 1024 * 1024

        const val TOO_LARGE = "That file is too large to be an AnDB database"

        const val TEXT_FILE = "That’s a text file, not an AnDB database. To add a CSV file’s rows, open a " +
            "database (or create one starting from a CSV file) and choose Import CSV… from its menu."

        /** How a SQLite file starts, and how much of a file to look at to tell text from a database. */
        const val SQLITE_HEADER = "SQLite format 3"
        const val TEXT_SNIFF_BYTES = 4096

        /** What an AnDB database's schema holds, by type: its own tables, and indexes on them. */
        val EXPECTED = mapOf(
            "table" to Regex(
                "andb_(meta|field|view|filter|photo)|record|(list|link|links)_f\\d+|sqlite_sequence|android_metadata"
            ),
            "index" to Regex("u_f\\d+|sqlite_autoindex_\\w+")
        )
    }
}
