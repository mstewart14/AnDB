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

        val columns = SQLiteDatabase.openDatabase(File(dir, "$id.db").path, null, SQLiteDatabase.OPEN_READONLY)
            .use { db ->
                db.rawQuery("PRAGMA table_info(record)", null).use { c ->
                    buildList { while (c.moveToNext()) add(c.getString(1) to c.getString(2)) }
                }
            }
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
}
