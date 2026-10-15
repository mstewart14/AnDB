package ca.stewartclan.andb.data

import android.database.sqlite.SQLiteDatabase
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** List views of AnDB databases. Every database has at least one. */
class ViewRepository(private val databases: DatabaseRepository) {
    private val json = Json { ignoreUnknownKeys = true }
    private val columnsSerializer = ListSerializer(ViewColumn.serializer())
    private val sortSerializer = ListSerializer(SortKey.serializer())

    /**
     * The views of database [id] in order, creating the first one, named [defaultName] and showing
     * all fields, if there are none yet.
     */
    suspend fun views(id: String, defaultName: String): List<ListView> {
        val views = databases.read(id) { readViews() }
        if (views.isNotEmpty()) return views
        return databases.write(id) {
            if (readViews().isEmpty()) insertView(ListView(0, 0, defaultName, emptyList(), emptyList()))
            readViews()
        }
    }

    /** The id of the view last shown in database [id], if any. */
    suspend fun currentViewId(id: String): Long? = databases.read(id) {
        getMeta(DatabaseFile.META_CURRENT_VIEW)?.toLongOrNull()
    }

    suspend fun setCurrentView(id: String, viewId: Long) = databases.write(id) {
        setMeta(DatabaseFile.META_CURRENT_VIEW, viewId.toString())
    }

    /** Saves [view], adding it after the others if its id is 0, and returns its id. */
    suspend fun save(id: String, view: ListView): Long = databases.write(id) {
        if (view.id == 0L) {
            insertView(view)
        } else {
            execSQL(
                "UPDATE andb_view SET name = ?, columns = ?, sort = ? WHERE id = ?",
                arrayOf(view.name, encodeColumns(view), encodeSort(view), view.id)
            )
            view.id
        }
    }

    /** Deletes view [viewId] unless it's the only one. */
    suspend fun delete(id: String, viewId: Long) = databases.write(id) {
        check(longQuery("SELECT COUNT(*) FROM andb_view") > 1) { "A database keeps at least one list view" }
        execSQL("DELETE FROM andb_view WHERE id = ?", arrayOf(viewId))
    }

    private fun SQLiteDatabase.insertView(view: ListView): Long {
        val position = longQuery("SELECT COALESCE(MAX(position) + 1, 0) FROM andb_view")
        execSQL(
            "INSERT INTO andb_view (position, name, columns, sort) VALUES (?, ?, ?, ?)",
            arrayOf(position, view.name, encodeColumns(view), encodeSort(view))
        )
        return longQuery("SELECT last_insert_rowid()")
    }

    /** Views with columns and sort keys of fields that no longer exist left out. */
    private fun SQLiteDatabase.readViews(): List<ListView> {
        val fieldIds = readFields().map { it.id }.toSet()
        return rawQuery("SELECT id, position, name, columns, sort FROM andb_view ORDER BY position", null).use { c ->
            val id = c.getColumnIndexOrThrow("id")
            val position = c.getColumnIndexOrThrow("position")
            val name = c.getColumnIndexOrThrow("name")
            val columns = c.getColumnIndexOrThrow("columns")
            val sort = c.getColumnIndexOrThrow("sort")
            buildList {
                while (c.moveToNext()) {
                    add(
                        ListView(
                            id = c.getLong(id),
                            position = c.getInt(position),
                            name = c.getString(name),
                            columns = json.decodeFromString(columnsSerializer, c.getString(columns))
                                .filter { it.fieldId in fieldIds },
                            sort = json.decodeFromString(sortSerializer, c.getString(sort))
                                .filter { it.fieldId in fieldIds }
                        )
                    )
                }
            }
        }
    }

    private fun encodeColumns(view: ListView) = json.encodeToString(columnsSerializer, view.columns)

    private fun encodeSort(view: ListView) = json.encodeToString(sortSerializer, view.sort)
}
