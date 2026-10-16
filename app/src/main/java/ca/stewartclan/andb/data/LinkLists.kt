package ca.stewartclan.andb.data

import android.database.sqlite.SQLiteDatabase

// Link fields to records allowing several links: each record's links are rows of the field's
// `links_f<id>` table (see DatabaseFile), in the order made. Writes run inside a transaction.

internal fun SQLiteDatabase.createLinkListTable(fieldId: Long) {
    execSQL(
        "CREATE TABLE IF NOT EXISTS ${DatabaseFile.linkListTable(fieldId)} " +
            "(record_id INTEGER NOT NULL, uri TEXT NOT NULL, name TEXT NOT NULL, PRIMARY KEY (record_id, uri))"
    )
}

/** Sets record [recordId]'s links in Link field [fieldId] to [links], in order; a repeat is kept once. */
internal fun SQLiteDatabase.writeLinkList(fieldId: Long, recordId: Long, links: List<LinkValue>) {
    val table = DatabaseFile.linkListTable(fieldId)
    execSQL("DELETE FROM $table WHERE record_id = ?", arrayOf(recordId))
    links.forEach {
        execSQL(
            "INSERT OR IGNORE INTO $table (record_id, uri, name) VALUES (?, ?, ?)",
            arrayOf(recordId, it.uri, it.name)
        )
    }
}

/** Each record's links in Link field [fieldId], by record id; all records, or only [onlyId]. */
internal fun SQLiteDatabase.readLinkLists(fieldId: Long, onlyId: Long?): Map<Long, List<LinkValue>> {
    val where = if (onlyId != null) "WHERE record_id = $onlyId" else ""
    val sql = "SELECT record_id, uri, name FROM ${DatabaseFile.linkListTable(fieldId)} $where ORDER BY rowid"
    val rows = rawQuery(sql, null).use { c ->
        buildList { while (c.moveToNext()) add(c.getLong(0) to LinkValue(c.getString(1), c.getString(2), null)) }
    }
    return rows.groupBy({ it.first }, { it.second })
}

/**
 * If Link field [old] is about to allow several links as [new], moves each record's link from its
 * record column and snapshot table into the link list table. The column goes when the record
 * table is rebuilt.
 */
internal fun SQLiteDatabase.startMultipleLinks(old: FieldDef, new: FieldDef) {
    if (old.type != FieldType.LINK || old.isMultiLink || !new.isMultiLink) return
    createLinkListTable(old.id)
    val column = DatabaseFile.columnName(old.id)
    val snapshots = DatabaseFile.linkTable(old.id)
    execSQL(
        "INSERT INTO ${DatabaseFile.linkListTable(old.id)} (record_id, uri, name) " +
            "SELECT r._id, r.$column, COALESCE(s.name, r.$column) FROM record r " +
            "LEFT JOIN $snapshots s ON s.record_id = r._id WHERE r.$column IS NOT NULL"
    )
    execSQL("DROP TABLE IF EXISTS $snapshots")
}

/**
 * If Link field [old] allowed several links and [new] doesn't, keeps each record's first one in
 * the new record column and snapshot table, then drops the link list table.
 */
internal fun SQLiteDatabase.endMultipleLinks(old: FieldDef, new: FieldDef) {
    if (new.type != FieldType.LINK || new.isMultiLink || !old.isMultiLink) return
    val list = DatabaseFile.linkListTable(new.id)
    val first = "SELECT MIN(rowid) FROM $list WHERE record_id = l.record_id"
    createLinkTable(new.id)
    execSQL(
        "UPDATE record SET ${DatabaseFile.columnName(new.id)} = " +
            "(SELECT uri FROM $list WHERE record_id = record._id ORDER BY rowid LIMIT 1)"
    )
    execSQL(
        "INSERT OR REPLACE INTO ${DatabaseFile.linkTable(new.id)} (record_id, name) " +
            "SELECT record_id, name FROM $list l WHERE rowid = ($first)"
    )
    execSQL("DROP TABLE $list")
}

/**
 * If Link field [old] now links somewhere else as [new], points its links there, in the layout it
 * has before any other change: those in [relinked] (by record id, or by link list row when [old]
 * allows several) are replaced, the rest stay as they were; with no [relinked], every link is
 * cleared.
 */
internal fun SQLiteDatabase.repointLinks(old: FieldDef, new: FieldDef, relinked: Map<Long, LinkValue>?) {
    if (old.type != FieldType.LINK || new.type != FieldType.LINK || old.linksTheSameWay(new)) return
    if (old.isMultiLink) {
        val list = DatabaseFile.linkListTable(old.id)
        if (relinked == null) execSQL("DELETE FROM $list")
        // Two links of one record may now find the same record; it's kept once.
        relinked?.forEach { (row, link) ->
            execSQL(
                "UPDATE OR REPLACE $list SET uri = ?, name = ? WHERE rowid = ?",
                arrayOf(link.uri, link.name, row)
            )
        }
        return
    }
    val column = DatabaseFile.columnName(old.id)
    val snapshots = DatabaseFile.linkTable(old.id)
    if (relinked == null) {
        execSQL("UPDATE record SET $column = NULL")
        execSQL("DELETE FROM $snapshots")
    }
    relinked?.forEach { (recordId, link) ->
        execSQL("UPDATE record SET $column = ? WHERE _id = ?", arrayOf(link.uri, recordId))
        execSQL(
            "INSERT OR REPLACE INTO $snapshots (record_id, name, thumbnail) VALUES (?, ?, ?)",
            arrayOf(recordId, link.name, link.thumbnail)
        )
    }
}
