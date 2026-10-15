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
import org.junit.Assert.assertTrue
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

    @Test
    fun requiredFieldsMustBeFilledToInsert() = runBlocking {
        val id = repository.create("Books")
        val title = repository.addField(id, "Title", FieldType.STRING, required = true)
        val tags = repository.addField(
            id,
            "Tags",
            FieldType.LIST,
            ListProps(listOf("a"), multiple = true),
            required = true
        )

        assertRefused(listOf("Title", "Tags")) { records.create(id) }
        assertRefused(listOf("Tags")) { records.insert(id, mapOf(title.id to "Dune"), emptyMap()) }
        assertRefused(listOf("Title")) { records.insert(id, mapOf(title.id to "  "), mapOf(tags.id to listOf("a"))) }
        assertEquals(emptyList<Long>(), records.recordIds(id))

        val r = records.insert(id, mapOf(title.id to "Dune"), mapOf(tags.id to listOf("a")))
        assertEquals("Dune", records.record(id, r)!!.values[title.id])
    }

    @Test
    fun saveCannotEmptyARequiredField() = runBlocking {
        val id = repository.create("Books")
        val title = repository.addField(id, "Title", FieldType.STRING, required = true)
        val note = repository.addField(id, "Note", FieldType.NOTE)
        val r = records.insert(id, mapOf(title.id to "Dune"), emptyMap())

        assertRefused(listOf("Title")) { records.save(id, r, mapOf(title.id to null), emptyMap()) }
        // Fields not given keep their stored value, which counts.
        records.save(id, r, mapOf(note.id to "Signed"), emptyMap())

        val record = records.record(id, r)!!
        assertEquals("Dune", record.values[title.id])
        assertEquals("Signed", record.values[note.id])
    }

    @Test
    fun recordsFromBeforeAFieldWasRequiredMustBeCompletedToChange() = runBlocking {
        val id = repository.create("Books")
        val title = repository.addField(id, "Title", FieldType.STRING)
        val rating = repository.addField(id, "Rating", FieldType.RATING)
        val r = records.create(id)
        repository.updateField(id, FieldDef(title.id, 0, "Title", FieldType.STRING, NoProps, required = true))

        assertRefused(listOf("Title")) { records.save(id, r, mapOf(rating.id to 4L), emptyMap()) }
        assertRefused(listOf("Title")) { records.duplicate(id, r) }

        records.save(id, r, mapOf(title.id to "Dune", rating.id to 4L), emptyMap())
        assertEquals(4L, records.record(id, r)!!.values[rating.id])
    }

    @Test
    fun zeroStarsDoesNotFillARequiredRating() = runBlocking<Unit> {
        val id = repository.create("Books")
        val rating = repository.addField(id, "Rating", FieldType.RATING, required = true)

        assertRefused(listOf("Rating")) { records.insert(id, mapOf(rating.id to 0L), emptyMap()) }
        records.insert(id, mapOf(rating.id to 3L), emptyMap())
    }

    private suspend fun assertRefused(fields: List<String>, block: suspend () -> Unit) {
        val error = runCatching { block() }.exceptionOrNull()
        assertTrue("expected refusal, got $error", error is RequiredFieldsMissingException)
        assertEquals(fields, (error as RequiredFieldsMissingException).fields)
    }

    @Test
    fun linksKeepTheirSnapshotThroughSaveDuplicateAndDelete() = runBlocking {
        val id = repository.create("Books")
        val cover = repository.addField(id, "Cover", FieldType.LINK)
        val dune = LinkValue("content://photos/1", "dune.jpg", byteArrayOf(1, 2, 3))
        val r = records.insert(id, mapOf(cover.id to dune), emptyMap())

        assertEquals(dune, records.record(id, r)!!.values[cover.id])
        val copy = records.duplicate(id, r)
        assertEquals(dune, records.record(id, copy)!!.values[cover.id])

        records.save(id, r, mapOf(cover.id to null), emptyMap())
        assertNull(records.record(id, r)!!.values[cover.id])
        records.delete(id, copy)
        assertEquals(emptyList<String>(), repository.distinctValues(id, cover.id))
    }

    @Test
    fun linksSortAndListByName() = runBlocking {
        val id = repository.create("Books")
        val cover = repository.addField(id, "Cover", FieldType.LINK)
        val z = records.insert(id, mapOf(cover.id to LinkValue("content://p/1", "zebra.jpg", null)), emptyMap())
        val a = records.insert(id, mapOf(cover.id to LinkValue("content://p/2", "Apple.jpg", null)), emptyMap())

        assertEquals(listOf(a, z), records.records(id, listOf(SortKey(cover.id))).map { it.id })
        assertEquals(listOf("Apple.jpg", "zebra.jpg"), repository.distinctValues(id, cover.id))
    }

    @Test
    fun becomingALinkClearsValuesAndLeavingDropsSnapshots() = runBlocking {
        val id = repository.create("Books")
        val f = repository.addField(id, "Cover", FieldType.STRING)
        val r = records.insert(id, mapOf(f.id to "not a link"), emptyMap())

        repository.updateField(id, FieldDef(f.id, 0, "Cover", FieldType.LINK, LinkProps()))
        assertNull(records.record(id, r)!!.values[f.id])
        records.save(id, r, mapOf(f.id to LinkValue("content://p/1", "a.jpg", null)), emptyMap())

        repository.updateField(id, FieldDef(f.id, 0, "Cover", FieldType.STRING, NoProps))
        assertEquals("content://p/1", records.record(id, r)!!.values[f.id])
        repository.deleteField(id, f.id)
    }

    @Test
    fun recordIdsAreNeverReused() = runBlocking {
        val id = repository.create("Books")
        records.create(id)
        val last = records.create(id)
        records.delete(id, last)

        assertTrue(records.create(id) > last)
    }

    @Test
    fun recordLinksAreStoredAndChangingTheTargetClearsThem() = runBlocking {
        val authors = repository.create("Authors")
        val name = repository.addField(authors, "Name", FieldType.STRING)
        val leGuin = records.insert(authors, mapOf(name.id to "Le Guin"), emptyMap())
        val books = repository.create("Books")
        val author = repository.addField(books, "Author", FieldType.LINK, LinkProps(LinkSource.RECORD, authors))
        val link = LinkValue(RecordRef(authors, leGuin).uri, "Le Guin", null)
        val book = records.insert(books, mapOf(author.id to link), emptyMap())

        assertEquals(link, records.record(books, book)!!.values[author.id])

        repository.updateField(books, author.copy(props = LinkProps(LinkSource.RECORD, books)))
        assertNull(records.record(books, book)!!.values[author.id])
    }
}
