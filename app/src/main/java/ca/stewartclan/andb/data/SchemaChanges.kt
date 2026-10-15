package ca.stewartclan.andb.data

import android.database.sqlite.SQLiteDatabase

// Schema changes behind DatabaseRepository's field operations. Each runs inside its transaction.

/** Adds the record column for a new or newly stored [field]. */
internal fun SQLiteDatabase.addColumn(field: FieldDef, idGenerator: IdGenerator) {
    if (field.isMultiList) createListTable(field.id)
    if (field.type == FieldType.LINK) createLinkTable(field.id)
    val sqlType = field.storage.sqlType ?: return
    val column = DatabaseFile.columnName(field.id)
    execSQL("ALTER TABLE record ADD COLUMN $column $sqlType")
    if (field.type == FieldType.ID) {
        createUniqueIndex(column)
        backfillIds(field, idGenerator)
    }
}

/**
 * Adds, drops or retypes the record column of a field changing from [old] to [new] storage.
 * Values are converted with `CAST`; a field becoming an ID starts empty, ready for new IDs.
 */
internal fun SQLiteDatabase.changeColumn(
    fields: List<FieldDef>,
    old: FieldDef,
    new: FieldDef,
    idGenerator: IdGenerator
) {
    if (old.storage == Storage.NONE) {
        addColumn(new, idGenerator)
        return
    }
    rebuildRecordTable(
        recordColumns(fields.map { if (it.id == new.id) new else it }) { field, column ->
            when {
                field.id != new.id -> column
                new.type == FieldType.ID -> "NULL"
                else -> "CAST($column AS ${new.storage.sqlType})"
            }
        }
    )
}

/** Creates the side table for a field becoming multiple-choice and moves its column values in. */
internal fun SQLiteDatabase.startMultipleChoices(old: FieldDef) {
    createListTable(old.id)
    if (old.storage == Storage.NONE) return
    val column = DatabaseFile.columnName(old.id)
    execSQL(
        "INSERT INTO ${DatabaseFile.listTable(old.id)} (record_id, value) SELECT _id, CAST($column AS TEXT) " +
            "FROM record WHERE $column IS NOT NULL AND $column != ''"
    )
}

/**
 * Drops the side table of a field that is no longer multiple-choice, first keeping each
 * record's first choice in [new]'s column if it has one (and isn't an ID, which has its own).
 */
internal fun SQLiteDatabase.endMultipleChoices(new: FieldDef) {
    val listTable = DatabaseFile.listTable(new.id)
    if (new.storage != Storage.NONE && new.type != FieldType.ID) {
        execSQL(
            "UPDATE record SET ${DatabaseFile.columnName(new.id)} = (SELECT value FROM $listTable " +
                "WHERE record_id = record._id ORDER BY rowid LIMIT 1)"
        )
    }
    execSQL("DROP TABLE $listTable")
}

/**
 * Brings ID field [new] up to date after an update from [old]. A field that just became an ID
 * already has empty values (its column was added or rebuilt), so only a kind change clears them.
 */
internal fun SQLiteDatabase.updateIds(old: FieldDef, new: FieldDef, idGenerator: IdGenerator) {
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

/**
 * Starts or ends a field's life as a Link: a new Link starts empty with its snapshot table (other
 * values aren't links); a former Link loses its snapshots.
 */
internal fun SQLiteDatabase.updateLink(old: FieldDef, new: FieldDef) {
    if (new.type == FieldType.LINK && old.type != FieldType.LINK) {
        execSQL("UPDATE record SET ${DatabaseFile.columnName(new.id)} = NULL")
        createLinkTable(new.id)
    } else if (old.type == FieldType.LINK && new.type != FieldType.LINK) {
        execSQL("DROP TABLE IF EXISTS ${DatabaseFile.linkTable(old.id)}")
    }
}

internal fun SQLiteDatabase.createListTable(fieldId: Long) {
    execSQL(
        "CREATE TABLE IF NOT EXISTS ${DatabaseFile.listTable(fieldId)} " +
            "(record_id INTEGER NOT NULL, value TEXT NOT NULL, PRIMARY KEY (record_id, value))"
    )
}

internal fun SQLiteDatabase.createLinkTable(fieldId: Long) {
    execSQL(
        "CREATE TABLE IF NOT EXISTS ${DatabaseFile.linkTable(fieldId)} " +
            "(record_id INTEGER PRIMARY KEY, name TEXT NOT NULL, thumbnail BLOB)"
    )
}

internal fun SQLiteDatabase.createUniqueIndex(column: String) {
    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS u_$column ON record ($column)")
}
