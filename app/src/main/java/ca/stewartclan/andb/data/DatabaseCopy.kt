package ca.stewartclan.andb.data

import android.database.sqlite.SQLiteDatabase

/** Makes this copy of database [from]'s links and lookups into [from] point at itself, [to]. */
internal fun SQLiteDatabase.pointSelfReferencesAt(from: String, to: String, withRecords: Boolean) {
    readFields().forEach { field ->
        val props = when (val p = field.props) {
            is LinkProps -> p.takeIf { field.recordLink?.databaseId == from }?.copy(databaseId = to)
            is ListProps -> p.lookup?.takeIf {
                it.databaseId == from
            }?.let { p.copy(lookup = it.copy(databaseId = to)) }
            else -> null
        } ?: return@forEach
        execSQL("UPDATE andb_field SET props = ? WHERE id = ?", arrayOf(FieldProps.encode(props), field.id))
        if (withRecords && props is LinkProps) {
            val column = DatabaseFile.columnName(field.id)
            val (old, new) = RecordRef(from, 0).uri.dropLast(1) to RecordRef(to, 0).uri.dropLast(1)
            execSQL(
                "UPDATE record SET $column = replace($column, ?, ?) WHERE $column LIKE ?",
                arrayOf(old, new, "$old%")
            )
        }
    }
}

/** Removes every record, with their choices and link snapshots, and restarts ID counters. */
internal fun SQLiteDatabase.clearRecords() {
    val fields = readFields()
    execSQL("DELETE FROM record")
    fields.filter { it.isMultiList }.forEach { execSQL("DELETE FROM ${DatabaseFile.listTable(it.id)}") }
    fields.filter { it.type == FieldType.LINK }.forEach { execSQL("DELETE FROM ${DatabaseFile.linkTable(it.id)}") }
    fields.filter { it.type == FieldType.ID }.forEach { deleteMeta(DatabaseFile.nextIdKey(it.id)) }
    // Record ids restart too; sqlite_sequence exists once an AUTOINCREMENT table has had a row.
    val hasSequence = longQuery("SELECT COUNT(*) FROM sqlite_master WHERE name = 'sqlite_sequence'") > 0
    if (hasSequence) execSQL("DELETE FROM sqlite_sequence WHERE name = 'record'")
}
