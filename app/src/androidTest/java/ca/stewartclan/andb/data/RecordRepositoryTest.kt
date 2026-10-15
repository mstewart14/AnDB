package ca.stewartclan.andb.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordRepositoryTest {
    private lateinit var dir: File
    private var now = Instant.parse("2026-10-15T08:00:00Z")
    private lateinit var repository: DatabaseRepository
    private lateinit var records: RecordRepository

    @Before
    fun setUp() {
        dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "record-test")
        dir.deleteRecursively()
        repository = DatabaseRepository(dir, clock = { now })
        records = RecordRepository(repository)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun saveStoresValuesChoicesAndModifiedTime() = runBlocking {
        val id = repository.create("Books")
        val title = repository.addField(id, "Title", FieldType.STRING)
        val pages = repository.addField(id, "Pages", FieldType.INTEGER)
        val tags = repository.addField(id, "Tags", FieldType.LIST, ListProps(listOf("a", "b"), multiple = true))
        val r = records.create(id)
        now = Instant.parse("2026-10-16T09:30:00Z")

        records.save(id, r, mapOf(title.id to "Dune", pages.id to 412L), mapOf(tags.id to listOf("b", "a", "b")))

        val record = records.record(id, r)!!
        assertEquals("Dune", record.values[title.id])
        assertEquals(412L, record.values[pages.id])
        assertEquals(listOf("b", "a"), record.choices[tags.id])
        assertEquals("2026-10-15T08:00:00Z", record.created)
        assertEquals("2026-10-16T09:30:00Z", record.modified)
    }

    @Test
    fun saveLeavesIdsAndUnknownFieldsAlone() = runBlocking {
        val id = repository.create("Books")
        val no = repository.addField(id, "No.", FieldType.ID)
        val r = records.create(id)

        records.save(id, r, mapOf(no.id to 99L, 12345L to "x"), emptyMap())

        assertEquals(1L, records.record(id, r)!!.values[no.id])
    }

    @Test
    fun duplicateCopiesValuesAndChoicesWithNewIdAndTimes() = runBlocking {
        val id = repository.create("Books")
        val no = repository.addField(id, "No.", FieldType.ID)
        val title = repository.addField(id, "Title", FieldType.STRING)
        val tags = repository.addField(id, "Tags", FieldType.LIST, ListProps(listOf("a", "b"), multiple = true))
        val r = records.create(id)
        records.save(id, r, mapOf(title.id to "Dune"), mapOf(tags.id to listOf("b", "a")))
        now = Instant.parse("2026-10-17T00:00:00Z")

        val copy = records.record(id, records.duplicate(id, r))!!

        assertNotEquals(r, copy.id)
        assertEquals("Dune", copy.values[title.id])
        assertEquals(listOf("b", "a"), copy.choices[tags.id])
        assertEquals(2L, copy.values[no.id])
        assertEquals("2026-10-17T00:00:00Z", copy.created)
    }

    @Test
    fun deleteRemovesRecordAndItsChoices() = runBlocking {
        val id = repository.create("Books")
        val tags = repository.addField(id, "Tags", FieldType.LIST, ListProps(listOf("a"), multiple = true))
        val r1 = records.create(id)
        val r2 = records.create(id)
        records.save(id, r1, emptyMap(), mapOf(tags.id to listOf("a")))
        records.save(id, r2, emptyMap(), mapOf(tags.id to listOf("a")))

        records.delete(id, r1)

        assertNull(records.record(id, r1))
        assertEquals(listOf(r2), records.recordIds(id))
        assertEquals(listOf("a"), repository.distinctValues(id, tags.id))
        assertEquals(1L, repository.databases.value!!.single().recordCount)
    }

    @Test
    fun choicesForFixedItemsAndLookups() = runBlocking {
        val authors = repository.create("Authors")
        val name = repository.addField(authors, "Name", FieldType.STRING)
        val a = records.create(authors)
        records.save(authors, a, mapOf(name.id to "Le Guin"), emptyMap())
        val books = repository.create("Books")
        val fixed = repository.addField(books, "Genre", FieldType.LIST, ListProps(listOf("SF", "History")))
        val lookup = repository.addField(books, "Author", FieldType.LIST, ListProps(lookup = Lookup(authors, name.id)))

        assertEquals(listOf("SF", "History"), records.choicesFor(fixed))
        assertEquals(listOf("Le Guin"), records.choicesFor(lookup))

        repository.delete(authors)
        assertEquals(emptyList<String>(), records.choicesFor(lookup))
    }
}
