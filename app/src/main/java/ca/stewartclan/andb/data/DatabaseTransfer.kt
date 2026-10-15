package ca.stewartclan.andb.data

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.core.database.sqlite.transaction
import java.io.File
import java.util.UUID

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

    /**
     * Adds the database in `.andb` file [bytes] and returns its id. It keeps its name, numbered if
     * taken ("Wines (2)"); links and lookups to itself point at the new database.
     */
    suspend fun import(bytes: ByteArray): String = repository.mutate {
        val taken = repository.readSummaries().map { it.name }
        withTempFile { temp ->
            temp.writeBytes(bytes)
            val id = UUID.randomUUID().toString()
            try {
                DatabaseFile.open(temp).use { db -> db.transaction { adopt(id, taken) } }
            } catch (e: SQLiteException) {
                throw IllegalStateException(NOT_ANDB, e)
            } catch (e: IllegalStateException) {
                // Not ours, or made by a newer AnDB; the message would name the scratch file.
                throw IllegalStateException(NOT_ANDB, e)
            }
            check(temp.renameTo(repository.fileFor(id))) { "Couldn't add the database" }
            id
        }
    }

    /** Makes this imported database database [id], under a name not among [taken]. */
    private fun SQLiteDatabase.adopt(id: String, taken: List<String>) {
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
    }
}
