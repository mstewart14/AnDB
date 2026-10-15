package ca.stewartclan.andb.data

import android.database.sqlite.SQLiteDatabase

/** Gives every record without a value in ID [field] a new one, oldest record first. */
internal fun SQLiteDatabase.backfillIds(field: FieldDef, generator: IdGenerator) {
    val column = DatabaseFile.columnName(field.id)
    val recordIds = rawQuery("SELECT _id FROM record WHERE $column IS NULL ORDER BY _id", null).use { c ->
        buildList { while (c.moveToNext()) add(c.getLong(0)) }
    }
    recordIds.forEach { recordId ->
        execSQL("UPDATE record SET $column = ? WHERE _id = ?", arrayOf(nextIdValue(field, generator), recordId))
    }
}

/** The value for the next record in ID [field]; an increment advances its stored counter. */
internal fun SQLiteDatabase.nextIdValue(field: FieldDef, generator: IdGenerator): Any {
    val props = field.props as IdProps
    val column = DatabaseFile.columnName(field.id)
    return when (props.kind) {
        IdKind.INCREMENT -> {
            val key = DatabaseFile.nextIdKey(field.id)
            val next = getMeta(key)?.toLong() ?: props.start
            setMeta(key, (next + 1).toString())
            next
        }
        IdKind.UUID_RANDOM -> generator.uuidRandom()
        IdKind.UUID_TIME -> generator.uuidTime()
        // 31^8 codes make a clash unlikely, but the unique index would reject one, so check.
        IdKind.SHORT_CODE -> generateSequence { generator.shortCode() }.first { code ->
            longQuery("SELECT COUNT(*) FROM record WHERE $column = ?", code) == 0L
        }
    }
}
