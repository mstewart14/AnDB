package ca.stewartclan.andb.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

// Reading and writing record rows, behind RecordRepository. Writes run inside its transactions.

/** `_id`, `_created`, `_modified` come before the field columns. */
private const val SYSTEM_COLUMNS = 3

/** Writes [values] and [choices] of fields that take them, and sets the modified time. */
internal fun SQLiteDatabase.writeValues(
    fields: List<FieldDef>,
    recordId: Long,
    values: Map<Long, Any?>,
    choices: Map<Long, List<String>>,
    modified: String
) {
    val byId = fields.associateBy { it.id }
    val columns = values.filterKeys { fieldId ->
        byId[fieldId]?.let { it.storage != Storage.NONE && it.type != FieldType.ID } == true
    }
    val (links, plain) = columns.entries.partition { byId[it.key]?.type == FieldType.LINK }
    val sets = plain.joinToString("") { "${DatabaseFile.columnName(it.key)} = ?, " }
    val args = plain.map { it.value } + modified + recordId
    execSQL("UPDATE record SET ${sets}_modified = ? WHERE _id = ?", args.toTypedArray())
    // A link's uri goes in the column, its snapshot in the field's link table.
    links.forEach { (fieldId, value) -> writeLink(fieldId, recordId, value as? LinkValue) }
    values.filterKeys { byId[it]?.isMultiLink == true }.forEach { (fieldId, value) ->
        writeLinkList(fieldId, recordId, linkValues(value))
    }
    choices.filterKeys { byId[it]?.isMultiList == true }.forEach { (fieldId, chosen) ->
        writeChoices(fieldId, recordId, chosen)
    }
}

private fun SQLiteDatabase.writeChoices(fieldId: Long, recordId: Long, chosen: List<String>) {
    val table = DatabaseFile.listTable(fieldId)
    execSQL("DELETE FROM $table WHERE record_id = ?", arrayOf(recordId))
    chosen.distinct().forEach {
        execSQL("INSERT INTO $table (record_id, value) VALUES (?, ?)", arrayOf(recordId, it))
    }
}

/**
 * `ORDER BY` terms for [sort]: empty values last whichever the direction, text without regard
 * to case unless asked, and oldest record first among equals. Unknown fields are skipped.
 */
internal fun orderBy(fields: List<FieldDef>, sort: List<SortKey>): String {
    val byId = fields.associateBy { it.id }
    val terms = sort.mapNotNull { key ->
        val field = byId[key.fieldId] ?: return@mapNotNull null
        val expr = sortExpression(field) ?: return@mapNotNull null
        val collate = if (field.isTextual && !key.caseSensitive) " COLLATE NOCASE" else ""
        "($expr IS NULL), $expr$collate${if (key.descending) " DESC" else ""}"
    }
    return (terms + "_id").joinToString()
}

private fun sortExpression(field: FieldDef): String? = when {
    field.type == FieldType.CREATED -> "_created"
    field.type == FieldType.MODIFIED -> "_modified"
    field.isMultiList ->
        "(SELECT group_concat(value, ', ') FROM ${DatabaseFile.listTable(field.id)} WHERE record_id = record._id)"
    field.isMultiLink ->
        "(SELECT group_concat(name, ', ') FROM ${DatabaseFile.linkListTable(field.id)} WHERE record_id = record._id)"
    // Links sort by the linked item's name.
    field.type == FieldType.LINK ->
        "(SELECT name FROM ${DatabaseFile.linkTable(field.id)} WHERE record_id = record._id)"
    field.storage == Storage.NONE -> null
    else -> DatabaseFile.columnName(field.id)
}

/** Records with their values, all of them or only [onlyId], in [orderBy] order. */
internal fun SQLiteDatabase.readRecords(fields: List<FieldDef>, onlyId: Long?, orderBy: String = "_id"): List<Record> {
    val stored = fields.filter { it.storage != Storage.NONE }
    val columns = stored.joinToString("") { ", ${DatabaseFile.columnName(it.id)}" }
    val where = if (onlyId != null) "WHERE _id = $onlyId" else ""
    val choices = fields.filter { it.isMultiList }.associate { it.id to readChoices(it.id, onlyId) }
    val links = fields.filter { it.type == FieldType.LINK && !it.isMultiLink }
        .associate { it.id to readLinkSnapshots(it.id, onlyId) }
    val linkLists = fields.filter { it.isMultiLink }.associate { it.id to readLinkLists(it.id, onlyId) }
    return rawQuery("SELECT _id, _created, _modified$columns FROM record $where ORDER BY $orderBy", null).use { c ->
        buildList {
            while (c.moveToNext()) {
                val recordId = c.getLong(0)
                add(
                    Record(
                        id = recordId,
                        created = c.getString(1),
                        modified = c.getString(2),
                        values = stored.withIndex().associate { (i, f) ->
                            val value = c.value(i + SYSTEM_COLUMNS)
                            val snapshot = links[f.id]?.get(recordId)
                            f.id to if (f.type == FieldType.LINK && value is String) {
                                LinkValue(value, snapshot?.name ?: value, snapshot?.thumbnail)
                            } else {
                                value
                            }
                        } + linkLists.mapValues { (_, byRecord) -> byRecord[recordId].orEmpty() },
                        choices = choices.mapValues { (_, byRecord) -> byRecord[recordId].orEmpty() }
                    )
                )
            }
        }
    }
}

/** Each record's link snapshot in Link field [fieldId] (uri left empty), by record id. */
private fun SQLiteDatabase.readLinkSnapshots(fieldId: Long, onlyId: Long?): Map<Long, LinkValue> {
    val where = if (onlyId != null) "WHERE record_id = $onlyId" else ""
    return rawQuery("SELECT record_id, name, thumbnail FROM ${DatabaseFile.linkTable(fieldId)} $where", null)
        .use { c ->
            buildMap { while (c.moveToNext()) put(c.getLong(0), LinkValue("", c.getString(1), c.getBlob(2))) }
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
