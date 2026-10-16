package ca.stewartclan.andb.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTransferTest {
    private lateinit var dir: File
    private lateinit var repository: DatabaseRepository
    private lateinit var records: RecordRepository
    private lateinit var transfer: DatabaseTransfer

    @Before
    fun setUp() {
        dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "transfer-test")
        dir.deleteRecursively()
        repository = DatabaseRepository(dir)
        records = RecordRepository(repository)
        transfer = DatabaseTransfer(repository)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    /** Ids of Aromas and Wines; each wine links to an aroma and to the wine before it. */
    private suspend fun wines(): Pair<String, String> {
        val aromas = repository.create("Aromas")
        val aroma = repository.addField(aromas, "Aroma", FieldType.STRING)
        val lemon = records.insert(aromas, mapOf(aroma.id to "Lemon"), emptyMap())
        val wines = repository.create("Wines")
        val name = repository.addField(wines, "Name", FieldType.STRING)
        val smells = repository.addField(wines, "Smells of", FieldType.LINK, LinkProps(LinkSource.RECORD, aromas))
        val after = repository.addField(wines, "After", FieldType.LINK, LinkProps(LinkSource.RECORD, wines))
        val first = records.insert(
            wines,
            mapOf(name.id to "Arg", smells.id to LinkValue(RecordRef(aromas, lemon).uri, "Lemon", null)),
            emptyMap()
        )
        records.insert(
            wines,
            mapOf(name.id to "Mump", after.id to LinkValue(RecordRef(wines, first).uri, "Arg", null)),
            emptyMap()
        )
        return aromas to wines
    }

    private suspend fun field(db: String, name: String) = repository.fields(db).first { it.name == name }

    @Test
    fun designOnlyRoundTrip() = runBlocking {
        val (aromas, wines) = wines()

        val copy = transfer.import(transfer.export(wines, withRecords = false))

        assertEquals("Wines (2)", repository.databases.value!!.first { it.id == copy }.name)
        assertEquals(repository.fields(wines).map { it.name }, repository.fields(copy).map { it.name })
        assertTrue(records.records(copy).isEmpty())
        assertEquals(copy, field(copy, "After").recordLink?.databaseId)
        val smells = field(copy, "Smells of").recordLink!!
        assertEquals(aromas, smells.databaseId)
        assertEquals("Aromas", smells.missingName)
        // Nothing left behind: just the three databases.
        assertEquals(3, dir.listFiles { f -> f.extension == "db" }!!.size)
        assertTrue(dir.listFiles { f -> f.extension == "tmp" }!!.isEmpty())
    }

    @Test
    fun withRecordsLinksWithinTheCopyFollowIt() = runBlocking {
        val (aromas, wines) = wines()

        val copy = transfer.import(transfer.export(wines, withRecords = true))

        val copied = records.records(copy)
        assertEquals(2, copied.size)
        val after = field(copy, "After")
        assertEquals(RecordRef(copy, copied[0].id).uri, (copied[1].values[after.id] as LinkValue).uri)
        val smells = field(copy, "Smells of")
        assertEquals(RecordRef(aromas, 1).uri, (copied[0].values[smells.id] as LinkValue).uri)
        // The original is untouched.
        assertEquals(wines, field(wines, "After").recordLink?.databaseId)
        assertEquals(null, field(wines, "Smells of").recordLink?.missingName)
    }

    /** An export of a small database, changed by [tamper] as someone might before sending it. */
    private suspend fun tampered(tamper: SQLiteDatabase.() -> Unit): ByteArray {
        val id = repository.create("Wines")
        val name = repository.addField(id, "Name", FieldType.STRING)
        records.insert(id, mapOf(name.id to "Mump"), emptyMap())
        val file = File(dir, "tampered.bin")
        file.writeBytes(transfer.export(id, withRecords = true))
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.tamper() }
        return file.readBytes().also { file.delete() }
    }

    private suspend fun assertRefused(bytes: ByteArray) {
        val before = repository.readSummaries().map { it.name }
        try {
            transfer.import(bytes)
            fail("Imported a tampered file")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().startsWith("That file isn't an AnDB database"))
        }
        assertEquals(before, repository.readSummaries().map { it.name })
        assertTrue(dir.listFiles { f -> f.extension == "tmp" }!!.isEmpty())
    }

    @Test
    fun aFileWithTriggersOrViewsIsRefused() = runBlocking {
        assertRefused(tampered { execSQL("CREATE TRIGGER wipe AFTER UPDATE ON record BEGIN DELETE FROM record; END") })
        assertRefused(tampered { execSQL("CREATE VIEW everything AS SELECT * FROM record") })
    }

    @Test
    fun aFileWithoutASchemaVersionIsRefused() = runBlocking {
        assertRefused(tampered { version = 0 })
    }

    @Test
    fun aFileWithFieldsAnDbDoesNotKnowIsRefused() = runBlocking {
        assertRefused(tampered { execSQL("UPDATE andb_field SET type = 'hologram'") })
        assertRefused(tampered { execSQL("UPDATE andb_field SET type = 'list', props = '{not json'") })
    }

    @Test
    fun anUntamperedExportStillImports() = runBlocking {
        val bytes = tampered { }
        val id = transfer.import(bytes)
        assertEquals("Mump", records.records(id).single().values.values.single())
    }

    @Test
    fun aFileThatIsNotADatabaseIsRefused() = runBlocking {
        repository.create("Wines")
        try {
            transfer.import("Type,Category,Aroma\nFruit,Citrus,Lemon\n".toByteArray())
            fail("Imported a CSV file")
        } catch (e: IllegalStateException) {
            // A CSV file is pointed to where CSV files go.
            assertTrue(e.message.orEmpty().contains("Import CSV"))
        }
        // Anything else that isn't a database is simply refused.
        assertRefused(byteArrayOf(0, 1, 2, 3, 0, 5, 6, 7))
        assertEquals(listOf("Wines"), repository.readSummaries().map { it.name })
        assertTrue(dir.listFiles { f -> f.extension == "tmp" }!!.isEmpty())
    }
}
