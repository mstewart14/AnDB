package ca.stewartclan.andb.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
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

        repository.renameField(id, b.id, "Bee")
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

        repository.changeFieldType(id, pages.id, FieldType.INTEGER)
        repository.changeFieldType(id, note.id, FieldType.NOTE)

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

        repository.changeFieldType(id, f.id, FieldType.STRING)
        assertEquals("TEXT", recordColumns(id).toMap()["f${f.id}"])

        repository.changeFieldType(id, f.id, FieldType.SECTION)
        assertEquals(3, recordColumns(id).size)
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
