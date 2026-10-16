package ca.stewartclan.andb.data

import android.database.sqlite.SQLiteDatabase
import java.io.ByteArrayOutputStream

// Photos copied into a database (see StoredPhoto), behind RecordRepository. Writes run inside its
// transactions.

/** How much of a photo to read at a time: a cursor can't carry a whole large one. */
private const val CHUNK = 512 * 1024

/**
 * Sets Link field [fieldId] of record [recordId] to [link] (null for none): its uri in the record,
 * its snapshot in the link table, and a photo it brings along stored. A stored photo no record
 * shows any more is deleted.
 */
internal fun SQLiteDatabase.writeLink(fieldId: Long, recordId: Long, link: LinkValue?) {
    val column = DatabaseFile.columnName(fieldId)
    val old = rawQuery("SELECT $column FROM record WHERE _id = ?", arrayOf(recordId.toString()))
        .use { c -> if (c.moveToFirst()) c.getString(0) else null }
    val stored = link?.photo?.let { jpeg ->
        execSQL("INSERT INTO andb_photo (data) VALUES (?)", arrayOf(jpeg))
        LinkValue(StoredPhoto.uri(longQuery("SELECT last_insert_rowid()")), link.name, link.thumbnail)
    } ?: link
    execSQL("UPDATE record SET $column = ? WHERE _id = ?", arrayOf(stored?.uri, recordId))
    val table = DatabaseFile.linkTable(fieldId)
    if (stored == null) {
        execSQL("DELETE FROM $table WHERE record_id = ?", arrayOf(recordId))
    } else {
        execSQL(
            "INSERT OR REPLACE INTO $table (record_id, name, thumbnail) VALUES (?, ?, ?)",
            arrayOf(recordId, stored.name, stored.thumbnail)
        )
    }
    if (old != stored?.uri) deleteUnusedPhoto(old)
}

/** The stored photos record [recordId] shows, as uris. */
internal fun SQLiteDatabase.storedPhotos(recordId: Long): List<String> = photoColumns().mapNotNull { column ->
    rawQuery("SELECT $column FROM record WHERE _id = ?", arrayOf(recordId.toString()))
        .use { c -> if (c.moveToFirst()) c.getString(0) else null }
        ?.takeIf { StoredPhoto.isStored(it) }
}

/** Deletes stored photo [uri] if no record shows it (records copied from one share its photo). */
internal fun SQLiteDatabase.deleteUnusedPhoto(uri: String?) {
    val id = StoredPhoto.id(uri) ?: return
    val used = photoColumns().any { column ->
        longQuery("SELECT COUNT(*) FROM record WHERE $column = ?", uri.orEmpty()) > 0
    }
    if (!used) execSQL("DELETE FROM andb_photo WHERE id = ?", arrayOf(id))
}

/** Stored photo [id]'s JPEG, read a piece at a time; null if there's no such photo. */
internal fun SQLiteDatabase.readStoredPhoto(id: Long): ByteArray? {
    val size = rawQuery("SELECT length(data) FROM andb_photo WHERE id = ?", arrayOf(id.toString()))
        .use { c -> if (c.moveToFirst()) c.getLong(0) else null } ?: return null
    val out = ByteArrayOutputStream(size.toInt())
    var offset = 0L
    while (offset < size) {
        // substr() counts from 1.
        rawQuery("SELECT substr(data, ?, ?) FROM andb_photo WHERE id = ?", arrayOf("${offset + 1}", "$CHUNK", "$id"))
            .use { c -> if (c.moveToFirst()) out.write(c.getBlob(0)) }
        offset += CHUNK
    }
    return out.toByteArray()
}

/** Record columns of Link fields to photos. */
private fun SQLiteDatabase.photoColumns(): List<String> =
    readFields().filter { (it.props as? LinkProps)?.source == LinkSource.PHOTO }.map { DatabaseFile.columnName(it.id) }
