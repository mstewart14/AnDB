package ca.stewartclan.andb.data

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

    @Test
    fun aFileThatIsNotADatabaseIsRefused() = runBlocking {
        repository.create("Wines")
        try {
            transfer.import("not a database at all".toByteArray())
            fail("Imported a text file")
        } catch (_: IllegalStateException) {
            // Expected.
        }
        assertEquals(listOf("Wines"), repository.readSummaries().map { it.name })
        assertTrue(dir.listFiles { f -> f.extension == "tmp" }!!.isEmpty())
    }
}
