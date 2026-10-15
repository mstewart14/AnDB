package ca.stewartclan.andb.data

import android.database.DatabaseUtils
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseRepositoryTest {
    private lateinit var dir: File
    private lateinit var repository: DatabaseRepository

    @Before
    fun setUp() {
        dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "repo-test")
        dir.deleteRecursively()
        repository = DatabaseRepository(dir)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun createListsDatabasesSortedByName() = runBlocking {
        repository.create("wine cellar")
        repository.create("Books")

        val dbs = repository.databases.value!!
        assertEquals(listOf("Books", "wine cellar"), dbs.map { it.name })
        assertEquals(0L, dbs[0].recordCount)
        assertEquals(0L, dbs[0].fieldCount)
    }

    @Test
    fun renameChangesNameOnly() = runBlocking {
        val id = repository.create("Books")
        repository.rename(id, "Library")

        val db = repository.databases.value!!.single()
        assertEquals(id, db.id)
        assertEquals("Library", db.name)
    }

    @Test
    fun deleteRemovesFile() = runBlocking {
        val id = repository.create("Books")
        repository.delete(id)

        assertTrue(repository.databases.value!!.isEmpty())
        assertFalse(File(dir, "$id.db").exists())
    }

    @Test
    fun addFieldCreatesColumnsOnlyForStoredTypes() = runBlocking {
        val id = repository.create("Books")
        val title = repository.addField(id, "Title", FieldType.STRING)
        val header = repository.addField(id, "Details", FieldType.SECTION)
        val pages = repository.addField(id, "Pages", FieldType.INTEGER)

        assertEquals(listOf(title, header, pages), repository.fields(id))
        assertEquals(listOf(0, 1, 2), repository.fields(id).map { it.position })
        assertEquals(3L, repository.databases.value!!.single().fieldCount)

        val columns = recordColumns(id)
        assertEquals(
            listOf(
                "_id" to "INTEGER",
                "_created" to "TEXT",
                "_modified" to "TEXT",
                "f${title.id}" to "TEXT",
                "f${pages.id}" to "INTEGER"
            ),
            columns
        )
    }

    @Test
    fun ignoresFilesThatAreNotAnDbDatabases() = runBlocking {
        dir.mkdirs()
        File(dir, "junk.db").writeText("not a database")
        SQLiteDatabase.openOrCreateDatabase(File(dir, "other.db"), null).use {
            it.execSQL("CREATE TABLE t (x)")
        }
        repository.create("Books")

        assertEquals(listOf("Books"), repository.databases.value!!.map { it.name })
    }

    @Test
    fun renameAndMoveFields() = runBlocking {
        val id = repository.create("Books")
        val a = repository.addField(id, "A", FieldType.STRING)
        val b = repository.addField(id, "B", FieldType.STRING)
        val c = repository.addField(id, "C", FieldType.STRING)

        repository.updateField(id, b.id, "Bee", FieldType.STRING, NoProps)
        repository.moveField(id, c.id, 0)

        val fields = repository.fields(id)
        assertEquals(listOf("C", "A", "Bee"), fields.map { it.name })
        assertEquals(listOf(c.id, a.id, b.id), fields.map { it.id })
        assertEquals(listOf(0, 1, 2), fields.map { it.position })
    }

    @Test
    fun deleteFieldKeepsOtherValues() = runBlocking {
        val id = repository.create("Books")
        val title = repository.addField(id, "Title", FieldType.STRING)
        val pages = repository.addField(id, "Pages", FieldType.INTEGER)
        val read = repository.addField(id, "Read", FieldType.BOOLEAN)
        insertRecord(id, "f${title.id}" to "Dune", "f${pages.id}" to 412, "f${read.id}" to 1)

        repository.deleteField(id, pages.id)

        assertEquals(listOf("Title", "Read"), repository.fields(id).map { it.name })
        assertEquals(listOf(0, 1), repository.fields(id).map { it.position })
        assertEquals(
            listOf("_id", "_created", "_modified", "f${title.id}", "f${read.id}"),
            recordColumns(id).map { it.first }
        )
        assertEquals(listOf("Dune", "1"), readRecord(id, "f${title.id}", "f${read.id}"))
        assertEquals(1L, repository.databases.value!!.single().recordCount)
    }

    @Test
    fun changeFieldTypeConvertsOrKeepsValues() = runBlocking {
        val id = repository.create("Books")
        val pages = repository.addField(id, "Pages", FieldType.STRING)
        val note = repository.addField(id, "Note", FieldType.STRING)
        insertRecord(id, "f${pages.id}" to "412", "f${note.id}" to "Signed")

        repository.updateField(id, pages.id, "Pages", FieldType.INTEGER, NoProps)
        repository.updateField(id, note.id, "Note", FieldType.NOTE, NoProps)

        assertEquals(listOf(FieldType.INTEGER, FieldType.NOTE), repository.fields(id).map { it.type })
        assertEquals("INTEGER", recordColumns(id).toMap()["f${pages.id}"])
        val typeOfPages = openRaw(id).use { db ->
            db.rawQuery("SELECT typeof(f${pages.id}) FROM record", null).use {
                it.moveToFirst()
                it.getString(0)
            }
        }
        assertEquals("integer", typeOfPages)
        assertEquals(listOf("412", "Signed"), readRecord(id, "f${pages.id}", "f${note.id}"))
    }

    @Test
    fun changeFieldTypeToAndFromNoStorage() = runBlocking {
        val id = repository.create("Books")
        val f = repository.addField(id, "Details", FieldType.SECTION)
        assertEquals(3, recordColumns(id).size)

        repository.updateField(id, f.id, "Details", FieldType.STRING, NoProps)
        assertEquals("TEXT", recordColumns(id).toMap()["f${f.id}"])

        repository.updateField(id, f.id, "Details", FieldType.SECTION, NoProps)
        assertEquals(3, recordColumns(id).size)
    }

    @Test
    fun newIdFieldNumbersExistingRecordsOldestFirst() = runBlocking {
        val id = repository.create("Books")
        repeat(3) { repository.createRecord(id) }
        val f = repository.addField(id, "No.", FieldType.ID, IdProps(start = 100))

        assertEquals(listOf("100", "101", "102"), column(id, "f${f.id}"))
        assertEquals(listOf("integer"), column(id, "DISTINCT typeof(f${f.id})"))
        val next = repository.createRecord(id)
        assertEquals("103", value(id, "f${f.id}", next))
    }

    @Test
    fun incrementNeverReusesNumbers() = runBlocking {
        val id = repository.create("Books")
        val f = repository.addField(id, "No.", FieldType.ID)
        repository.createRecord(id)
        val second = repository.createRecord(id)
        openRaw(id).use { it.execSQL("DELETE FROM record WHERE _id = $second") }

        val third = repository.createRecord(id)
        assertEquals("3", value(id, "f${f.id}", third))
    }

    @Test
    fun raisingStartMovesCounterButLoweringDoesNot() = runBlocking {
        val id = repository.create("Books")
        val f = repository.addField(id, "No.", FieldType.ID)
        repository.createRecord(id)

        repository.updateField(id, f.id, "No.", FieldType.ID, IdProps(start = 500))
        assertEquals("500", value(id, "f${f.id}", repository.createRecord(id)))
        repository.updateField(id, f.id, "No.", FieldType.ID, IdProps(start = 1))
        assertEquals("501", value(id, "f${f.id}", repository.createRecord(id)))
    }

    @Test
    fun otherKindsGenerateTheirFormats() = runBlocking {
        val id = repository.create("Books")
        val v4 = repository.addField(id, "A", FieldType.ID, IdProps(IdKind.UUID_RANDOM))
        val v7 = repository.addField(id, "B", FieldType.ID, IdProps(IdKind.UUID_TIME))
        val code = repository.addField(id, "C", FieldType.ID, IdProps(IdKind.SHORT_CODE))
        val record = repository.createRecord(id)

        assertEquals(4, UUID.fromString(value(id, "f${v4.id}", record)).version())
        assertEquals(7, UUID.fromString(value(id, "f${v7.id}", record)).version())
        assertTrue(Regex("[A-Z2-9]{4}-[A-Z2-9]{4}").matches(value(id, "f${code.id}", record)))
    }

    @Test
    fun changingKindRegeneratesEveryId() = runBlocking {
        val id = repository.create("Books")
        val f = repository.addField(id, "No.", FieldType.ID)
        repeat(3) { repository.createRecord(id) }

        repository.updateField(id, f.id, "No.", FieldType.ID, IdProps(IdKind.SHORT_CODE))

        val codes = column(id, "f${f.id}")
        assertEquals(3, codes.toSet().size)
        assertTrue(codes.all { it != null && Regex("[A-Z2-9]{4}-[A-Z2-9]{4}").matches(it) })
        assertEquals(0L, metaCount(id, "next_id.${f.id}"))
    }

    @Test
    fun textFieldBecomingIdGetsNewIds() = runBlocking {
        val id = repository.create("Books")
        val f = repository.addField(id, "Code", FieldType.STRING)
        insertRecord(id, "f${f.id}" to "same")
        insertRecord(id, "f${f.id}" to "same")

        repository.updateField(id, f.id, "Code", FieldType.ID, IdProps())

        assertEquals(listOf("1", "2"), column(id, "f${f.id}"))
    }

    @Test
    fun idStaysUniqueThroughTableRebuild() = runBlocking {
        val id = repository.create("Books")
        val f = repository.addField(id, "No.", FieldType.ID)
        val other = repository.addField(id, "Title", FieldType.STRING)
        repository.createRecord(id)
        repository.deleteField(id, other.id)

        assertEquals(1L, indexCount(id, "u_f${f.id}"))
        val duplicate = runCatching {
            insertRecord(id, "f${f.id}" to 1)
        }
        assertTrue(duplicate.exceptionOrNull() is SQLiteConstraintException)
    }

    @Test
    fun idStopsBeingIdWhenRetyped() = runBlocking {
        val id = repository.create("Books")
        val f = repository.addField(id, "No.", FieldType.ID)
        repository.createRecord(id)

        repository.updateField(id, f.id, "No.", FieldType.INTEGER, NoProps)

        assertEquals(0L, indexCount(id, "u_f${f.id}"))
        assertEquals(0L, metaCount(id, "next_id.${f.id}"))
        assertEquals(listOf("1"), column(id, "f${f.id}"))
    }

    @Test
    fun listBecomingMultipleKeepsEachValueAsAChoice() = runBlocking {
        val id = repository.create("Books")
        val f = repository.addField(id, "Genre", FieldType.LIST, ListProps(listOf("Fiction", "History")))
        insertRecord(id, "f${f.id}" to "Fiction")
        insertRecord(id, "f${f.id}" to "")
        insertRecord(id, "f${f.id}" to "History")

        repository.updateField(id, f.id, "Genre", FieldType.LIST, ListProps(listOf("Fiction", "History"), true))

        assertEquals(3, recordColumns(id).size)
        assertEquals(listOf("1:Fiction", "3:History"), listRows(id, f.id))
    }

    @Test
    fun listBecomingSingleKeepsFirstChoice() = runBlocking {
        val id = repository.create("Books")
        val f = repository.addField(id, "Genre", FieldType.LIST, ListProps(listOf("A", "B"), multiple = true))
        val r1 = repository.createRecord(id)
        val r2 = repository.createRecord(id)
        repository.createRecord(id)
        openRaw(id).use { db ->
            db.execSQL("INSERT INTO list_f${f.id} VALUES ($r1, 'B'), ($r1, 'A'), ($r2, 'A')")
        }

        repository.updateField(id, f.id, "Genre", FieldType.LIST, ListProps(listOf("A", "B")))

        assertEquals(listOf("B", "A", null), column(id, "f${f.id}"))
        assertEquals(0L, tableCount(id, "list_f${f.id}"))
    }

    @Test
    fun multiListToIdAndDeleteDropSideTable() = runBlocking {
        val id = repository.create("Books")
        val a = repository.addField(id, "A", FieldType.LIST, ListProps(listOf("x"), multiple = true))
        val b = repository.addField(id, "B", FieldType.LIST, ListProps(listOf("x"), multiple = true))
        repository.createRecord(id)
        assertEquals(1L, tableCount(id, "list_f${a.id}"))

        repository.updateField(id, a.id, "A", FieldType.ID, IdProps())
        repository.deleteField(id, b.id)

        assertEquals(listOf("1"), column(id, "f${a.id}"))
        assertEquals(0L, tableCount(id, "list_f${a.id}"))
        assertEquals(0L, tableCount(id, "list_f${b.id}"))
    }

    @Test
    fun distinctValuesForLookups() = runBlocking {
        val id = repository.create("Books")
        val text = repository.addField(id, "Author", FieldType.STRING)
        val multi = repository.addField(id, "Tags", FieldType.LIST, ListProps(listOf("x"), multiple = true))
        val no = repository.addField(id, "No.", FieldType.ID, IdProps(prefix = "B-", padding = 3))
        val section = repository.addField(id, "Details", FieldType.SECTION)
        val r1 = repository.createRecord(id)
        val r2 = repository.createRecord(id)
        repository.createRecord(id)
        openRaw(id).use { db ->
            db.execSQL("UPDATE record SET f${text.id} = 'le Guin' WHERE _id = $r1")
            db.execSQL("UPDATE record SET f${text.id} = 'Banks' WHERE _id = $r2")
            db.execSQL("INSERT INTO list_f${multi.id} VALUES ($r1, 'sf'), ($r2, 'sf'), ($r2, 'Classic')")
        }

        assertEquals(listOf("Banks", "le Guin"), repository.distinctValues(id, text.id))
        assertEquals(listOf("Classic", "sf"), repository.distinctValues(id, multi.id))
        assertEquals(listOf("B-001", "B-002", "B-003"), repository.distinctValues(id, no.id))
        assertEquals(emptyList<String>(), repository.distinctValues(id, section.id))
        assertEquals(emptyList<String>(), repository.distinctValues(id, 999))
    }

    private fun listRows(id: String, fieldId: Long): List<String> = openRaw(id).use { db ->
        db.rawQuery("SELECT record_id, value FROM list_f$fieldId ORDER BY record_id, rowid", null).use { c ->
            buildList { while (c.moveToNext()) add("${c.getLong(0)}:${c.getString(1)}") }
        }
    }

    private fun tableCount(id: String, name: String): Long = openRaw(id).use { db ->
        DatabaseUtils.longForQuery(
            db,
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(name)
        )
    }

    private fun column(id: String, expr: String): List<String?> = openRaw(id).use { db ->
        db.rawQuery("SELECT $expr FROM record ORDER BY _id", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
    }

    private fun value(id: String, column: String, recordId: Long): String = openRaw(id).use { db ->
        db.rawQuery("SELECT $column FROM record WHERE _id = $recordId", null).use { c ->
            c.moveToFirst()
            c.getString(0)
        }
    }

    private fun indexCount(id: String, name: String): Long = openRaw(id).use { db ->
        DatabaseUtils.longForQuery(
            db,
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = ?",
            arrayOf(name)
        )
    }

    private fun metaCount(id: String, key: String): Long = openRaw(id).use { db ->
        DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM andb_meta WHERE key = ?", arrayOf(key))
    }

    private fun openRaw(id: String) =
        SQLiteDatabase.openDatabase(File(dir, "$id.db").path, null, SQLiteDatabase.OPEN_READWRITE)

    private fun insertRecord(id: String, vararg values: Pair<String, Any>) = openRaw(id).use { db ->
        val columns = values.joinToString("") { ", ${it.first}" }
        val marks = values.joinToString("") { ", ?" }
        db.execSQL(
            "INSERT INTO record (_created, _modified$columns) VALUES ('t', 't'$marks)",
            values.map { it.second }.toTypedArray()
        )
    }

    private fun readRecord(id: String, vararg columns: String): List<String> = openRaw(id).use { db ->
        db.rawQuery("SELECT ${columns.joinToString()} FROM record", null).use { c ->
            c.moveToFirst()
            columns.indices.map { c.getString(it) }
        }
    }

    private fun recordColumns(id: String): List<Pair<String, String>> = openRaw(id).use { db ->
        db.rawQuery("PRAGMA table_info(record)", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(1) to c.getString(2)) }
        }
    }
}
