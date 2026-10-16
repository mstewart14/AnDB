package ca.stewartclan.andb.data

import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Link fields to records allowing several links. */
@RunWith(AndroidJUnit4::class)
class LinkListTest {
    private lateinit var dir: File
    private lateinit var repository: DatabaseRepository
    private lateinit var records: RecordRepository

    @Before
    fun setUp() {
        dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "link-list-test")
        dir.deleteRecursively()
        repository = DatabaseRepository(dir)
        records = RecordRepository(repository)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun link(databaseId: String, recordId: Long, name: String) =
        LinkValue(RecordRef(databaseId, recordId).uri, name, null)

    private suspend fun links(id: String, recordId: Long, field: FieldDef): List<LinkValue> =
        linkValues(records.record(id, recordId)!!.values[field.id])

    private fun rows(id: String, table: String): Long =
        SQLiteDatabase.openDatabase(File(dir, "$id.db").path, null, SQLiteDatabase.OPEN_READONLY).use {
            DatabaseUtils.longForQuery(it, "SELECT COUNT(*) FROM $table", null)
        }

    /** Wines with a Name and Aromas linking to several records of database "a". */
    private suspend fun wines(): Triple<String, FieldDef, FieldDef> {
        val id = repository.create("Wines")
        val name = repository.addField(id, "Name", FieldType.STRING)
        val aromas = repository.addField(
            id,
            "Aromas",
            FieldType.LINK,
            LinkProps(LinkSource.RECORD, AROMAS, multiple = true),
            required = true
        )
        return Triple(id, name, aromas)
    }

    @Test
    fun severalLinksAreStoredInOrderAndReplacedOnSave() = runBlocking {
        val (id, name, aromas) = wines()
        assertTrue(aromas.isMultiLink)
        assertEquals(Storage.NONE, aromas.storage)

        val cherry = link(AROMAS, 3, "Cherry")
        val plum = link(AROMAS, 1, "Plum")
        val r = records.insert(id, mapOf(name.id to "Mump", aromas.id to listOf(cherry, plum, cherry)), emptyMap())
        assertEquals(listOf(cherry, plum), links(id, r, aromas))

        val pepper = link(AROMAS, 2, "Pepper")
        records.save(id, r, mapOf(aromas.id to listOf(pepper, cherry)), emptyMap())
        assertEquals(listOf(pepper, cherry), links(id, r, aromas))
        assertEquals(listOf(2L, 3L), aromas.linkedRecordIds(links(id, r, aromas)))
    }

    @Test
    fun noLinksDoesNotFillARequiredField() = runBlocking<Unit> {
        val (id, name, aromas) = wines()
        try {
            records.insert(id, mapOf(name.id to "Mump", aromas.id to emptyList<LinkValue>()), emptyMap())
            throw AssertionError("stored without its required links")
        } catch (e: RequiredFieldsMissingException) {
            assertEquals(listOf("Aromas"), e.fields)
        }
    }

    @Test
    fun duplicateCopiesTheLinksAndDeleteRemovesThem() = runBlocking {
        val (id, name, aromas) = wines()
        val both = listOf(link(AROMAS, 1, "Plum"), link(AROMAS, 3, "Cherry"))
        val r = records.insert(id, mapOf(name.id to "Mump", aromas.id to both), emptyMap())

        val copy = records.duplicate(id, r)
        assertEquals(both, links(id, copy, aromas))

        records.delete(id, r)
        assertEquals(2, rows(id, DatabaseFile.linkListTable(aromas.id)))
        assertEquals(both, links(id, copy, aromas))
    }

    @Test
    fun backlinksFindRecordsLinkingAmongSeveral() = runBlocking {
        val aromasDb = repository.create("Aromas")
        val aroma = repository.addField(aromasDb, "Aroma", FieldType.STRING)
        val cherry = records.insert(aromasDb, mapOf(aroma.id to "Cherry"), emptyMap())
        val plum = records.insert(aromasDb, mapOf(aroma.id to "Plum"), emptyMap())
        val id = repository.create("Wines")
        val aromas = repository.addField(
            id,
            "Aromas",
            FieldType.LINK,
            LinkProps(LinkSource.RECORD, aromasDb, multiple = true)
        )
        val mump = records.insert(
            id,
            mapOf(aromas.id to listOf(link(aromasDb, plum, "Plum"), link(aromasDb, cherry, "Cherry"))),
            emptyMap()
        )
        val arg = records.insert(id, mapOf(aromas.id to listOf(link(aromasDb, cherry, "Cherry"))), emptyMap())

        assertEquals(listOf(mump, arg), records.backlinks(aromasDb, cherry).single().recordIds)
        assertEquals(listOf(mump), records.backlinks(aromasDb, plum).single().recordIds)
    }

    @Test
    fun allowingSeveralKeepsEachLinkAndBackToOneKeepsTheFirst() = runBlocking {
        val id = repository.create("Wines")
        val single = repository.addField(id, "Aromas", FieldType.LINK, LinkProps(LinkSource.RECORD, AROMAS))
        val plum = link(AROMAS, 1, "Plum")
        val r = records.insert(id, mapOf(single.id to plum), emptyMap())
        val empty = records.create(id)

        val several = single.copy(props = LinkProps(LinkSource.RECORD, AROMAS, multiple = true))
        repository.updateField(id, several)
        assertEquals(listOf(plum), links(id, r, several))
        assertEquals(emptyList<LinkValue>(), links(id, empty, several))

        val cherry = link(AROMAS, 3, "Cherry")
        records.save(id, r, mapOf(several.id to listOf(cherry, plum)), emptyMap())
        repository.updateField(id, single)
        assertEquals(cherry, records.record(id, r)!!.values[single.id])
        assertNull(records.record(id, empty)!!.values[single.id])
        assertEquals(mapOf(r to "Cherry"), repository.linkTitles(id, single.id))
    }

    @Test
    fun becomingSeveralLinksStartsEmptyAndLeavingDropsThem() = runBlocking {
        val id = repository.create("Wines")
        val notes = repository.addField(id, "Aromas", FieldType.STRING)
        val r = records.insert(id, mapOf(notes.id to "cherry"), emptyMap())

        val aromas = notes.copy(type = FieldType.LINK, props = LinkProps(LinkSource.RECORD, AROMAS, multiple = true))
        repository.updateField(id, aromas)
        assertEquals(emptyList<LinkValue>(), links(id, r, aromas))

        records.save(id, r, mapOf(aromas.id to listOf(link(AROMAS, 1, "Plum"))), emptyMap())
        repository.updateField(id, notes)
        assertNull(records.record(id, r)!!.values[notes.id])
        assertEquals(0, rows(id, "sqlite_master WHERE name = '${DatabaseFile.linkListTable(aromas.id)}'"))
    }

    @Test
    fun recordsSortByTheirLinkedTitles() = runBlocking {
        val (id, name, aromas) = wines()
        val b = records.insert(
            id,
            mapOf(name.id to "B", aromas.id to listOf(link(AROMAS, 2, "Pepper"))),
            emptyMap()
        )
        val a = records.insert(
            id,
            mapOf(name.id to "A", aromas.id to listOf(link(AROMAS, 1, "cherry"), link(AROMAS, 3, "Smoke"))),
            emptyMap()
        )

        val sorted = records.records(id, listOf(SortKey(aromas.id))).map { it.id }
        assertEquals(listOf(a, b), sorted)
        assertEquals(listOf("cherry", "Pepper", "Smoke"), repository.distinctValues(id, aromas.id))
    }

    @Test
    fun repointingRelinksEachLinkAndKeepsTheRest() = runBlocking {
        val (id, name, aromas) = wines()
        val r = records.insert(
            id,
            mapOf(name.id to "Mump", aromas.id to listOf(link(AROMAS, 1, "Plum"), link(AROMAS, 2, "Pepper"))),
            emptyMap()
        )
        val saved = repository.linkTitles(id, aromas.id)
        assertEquals(listOf("Plum", "Pepper"), saved.values.toList())
        val plumRow = saved.filterValues { it == "Plum" }.keys.single()

        val newPlum = link(NEW_AROMAS, 40, "Plum")
        val moved = aromas.copy(props = LinkProps(LinkSource.RECORD, NEW_AROMAS, multiple = true))
        repository.updateField(id, moved, Repoint(mapOf(plumRow to newPlum), emptyMap()))

        // Pepper wasn't found there: its old link stays, to show as unavailable.
        assertEquals(listOf(newPlum, link(AROMAS, 2, "Pepper")), links(id, r, moved))
        assertEquals(listOf(40L), moved.linkedRecordIds(links(id, r, moved)))
    }

    @Test
    fun repointingWithoutRelinkingClearsEveryLink() = runBlocking {
        val (id, name, aromas) = wines()
        val r = records.insert(id, mapOf(name.id to "Mump", aromas.id to listOf(link(AROMAS, 1, "Plum"))), emptyMap())

        val moved = aromas.copy(props = LinkProps(LinkSource.RECORD, NEW_AROMAS, multiple = true))
        repository.updateField(id, moved, Repoint(null, emptyMap()))

        assertEquals(emptyList<LinkValue>(), links(id, r, moved))
    }

    @Test
    fun copiesWithRecordsPointSelfLinksAtThemselves() = runBlocking {
        val id = repository.create("Books")
        val title = repository.addField(id, "Title", FieldType.STRING)
        val related = repository.addField(
            id,
            "Related",
            FieldType.LINK,
            LinkProps(LinkSource.RECORD, id, title.id, multiple = true)
        )
        val dune = records.insert(id, mapOf(title.id to "Dune"), emptyMap())
        val messiah = records.insert(
            id,
            mapOf(title.id to "Dune Messiah", related.id to listOf(link(id, dune, "Dune"))),
            emptyMap()
        )

        val copy = repository.duplicate(id, "Books 2", withRecords = true)
        assertEquals(listOf(link(copy, dune, "Dune")), links(copy, messiah, related))
        assertEquals(listOf(link(id, dune, "Dune")), links(id, messiah, related))

        val transfer = DatabaseTransfer(repository)
        val imported = transfer.import(transfer.export(id, withRecords = true))
        assertEquals(listOf(link(imported, dune, "Dune")), links(imported, messiah, related))

        val empty = repository.duplicate(id, "Books 3", withRecords = false)
        assertEquals(0, rows(empty, DatabaseFile.linkListTable(related.id)))
    }

    private companion object {
        const val AROMAS = "aromas-database"
        const val NEW_AROMAS = "new-aromas-database"
    }
}
