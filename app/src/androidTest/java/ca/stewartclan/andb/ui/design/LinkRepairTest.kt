package ca.stewartclan.andb.ui.design

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ca.stewartclan.andb.data.DatabaseRepository
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.FieldType
import ca.stewartclan.andb.data.LinkProps
import ca.stewartclan.andb.data.LinkSource
import ca.stewartclan.andb.data.LinkValue
import ca.stewartclan.andb.data.RecordRef
import ca.stewartclan.andb.data.RecordRepository
import ca.stewartclan.andb.ui.common.RecordTitles
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinkRepairTest {
    private lateinit var dir: File
    private lateinit var repository: DatabaseRepository
    private lateinit var records: RecordRepository
    private lateinit var repair: LinkRepair

    @Before
    fun setUp() {
        dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "repair-test")
        dir.deleteRecursively()
        repository = DatabaseRepository(dir)
        records = RecordRepository(repository)
        repair = LinkRepair(repository, RecordTitles(repository, records))
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    /** Aromas named [name], its Aroma field placed [aromaFirst] or after Type, with [aromas]; returns its id and Aroma field. */
    private suspend fun aromas(
        name: String,
        aromaFirst: Boolean,
        vararg aromas: Pair<String, String>
    ): Pair<String, FieldDef> {
        val id = repository.create(name)
        val aroma: FieldDef
        val type: FieldDef
        if (aromaFirst) {
            aroma = repository.addField(id, "Aroma", FieldType.STRING)
            type = repository.addField(id, "Type", FieldType.STRING)
        } else {
            type = repository.addField(id, "Type", FieldType.STRING)
            aroma = repository.addField(id, "Aroma", FieldType.STRING)
        }
        aromas.forEach { (t, a) -> records.insert(id, mapOf(type.id to t, aroma.id to a), emptyMap()) }
        return id to aroma
    }

    /** Wines linking to records 1 and 2 of [aromasId], saved as "5-Spice" and "Marmalade". */
    private suspend fun wines(aromasId: String, displayFieldId: Long): Pair<String, FieldDef> {
        val id = repository.create("Wines")
        val link = repository.addField(
            id,
            "Aromas",
            FieldType.LINK,
            LinkProps(LinkSource.RECORD, aromasId, displayFieldId)
        )
        listOf(1L to "5-Spice", 2L to "Marmalade").forEach { (recordId, title) ->
            records.insert(id, mapOf(link.id to LinkValue(RecordRef(aromasId, recordId).uri, title, null)), emptyMap())
        }
        return id to link
    }

    private val sample = arrayOf("Primary" to "5-Spice", "Primary" to "Marmalade", "Tertiary" to "Vanilla")

    @Test
    fun deletingADatabaseNamesItInLinksToIt() = runBlocking {
        val (old, aroma) = aromas("Aromas", aromaFirst = true, *sample)
        val (winesId, link) = wines(old, aroma.id)

        repository.delete(old)

        val props = repository.fields(winesId).first { it.id == link.id }.props as LinkProps
        assertEquals("Aromas", props.missingName)
        assertEquals(listOf(link.id), repair.broken(repository.fields(winesId)).map { it.id })
    }

    @Test
    fun aNewDatabaseOfTheSameNameTakesOverByItself() = runBlocking {
        val (old, aroma) = aromas("Aromas", aromaFirst = true, *sample)
        val (winesId, link) = wines(old, aroma.id)
        repository.delete(old)
        // Rebuilt with Aroma second, so it isn't the default display field any more.
        val (new, newAroma) = aromas("aromas", aromaFirst = false, "Primary" to "Vanilla", *sample.reversedArray())

        val repaired = repair.repairByName(winesId, repository.fields(winesId))

        assertEquals(1, repaired.size)
        assertEquals(2, repaired.single().plan.links.size)
        val props = repository.fields(winesId).first { it.id == link.id }.props as LinkProps
        assertEquals(new, props.databaseId)
        assertEquals(newAroma.id, props.displayFieldId)
        assertNull(props.missingName)
        val linked = records.records(winesId).map { (it.values[link.id] as LinkValue).name }
        assertEquals(listOf("5-Spice", "Marmalade"), linked)
        assertTrue(repair.broken(repository.fields(winesId)).isEmpty())
    }

    @Test
    fun withoutTheSameNameTheBestMatchIsOnlySuggested() = runBlocking {
        val (old, aroma) = aromas("Aromas", aromaFirst = true, *sample)
        val (winesId, link) = wines(old, aroma.id)
        repository.delete(old)
        val (other, _) = aromas("Smells", aromaFirst = false, *sample)
        aromas("Unrelated", aromaFirst = true, "x" to "y", "z" to "w")

        assertEquals(emptyList<Repaired>(), repair.repairByName(winesId, repository.fields(winesId)))
        val suggestion = repair.suggest(winesId, repository.fields(winesId).first { it.id == link.id })!!
        assertEquals(other, suggestion.databaseId)
        assertEquals(2, suggestion.plan.links.size)
        assertEquals(false, suggestion.byName)
    }
}
