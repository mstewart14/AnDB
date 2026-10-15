package ca.stewartclan.andb.data

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

@RunWith(AndroidJUnit4::class)
class ViewRepositoryTest {
    private lateinit var dir: File
    private lateinit var repository: DatabaseRepository
    private lateinit var records: RecordRepository
    private lateinit var views: ViewRepository

    @Before
    fun setUp() {
        dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "view-test")
        dir.deleteRecursively()
        repository = DatabaseRepository(dir)
        records = RecordRepository(repository)
        views = ViewRepository(repository)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun firstViewShowsAllFieldsAndIsCreatedOnce() = runBlocking {
        val id = repository.create("Books")
        val first = views.views(id, "All fields")
        val again = views.views(id, "Ignored")

        assertEquals(listOf("All fields"), first.map { it.name })
        assertTrue(first.single().showsAllFields)
        assertEquals(first, again)
        assertNull(views.currentViewId(id))
    }

    @Test
    fun saveAddsAndUpdatesViewsAndRemembersCurrent() = runBlocking {
        val id = repository.create("Books")
        val title = repository.addField(id, "Title", FieldType.STRING)
        views.views(id, "All fields")

        val viewId = views.save(
            id,
            ListView(0, 0, "By title", listOf(ViewColumn(title.id, ColumnWidth.WIDE)), listOf(SortKey(title.id)))
        )
        views.save(id, views.views(id, "x").last().copy(sort = listOf(SortKey(title.id, descending = true))))
        views.setCurrentView(id, viewId)

        val saved = views.views(id, "x").last()
        assertEquals("By title", saved.name)
        assertEquals(listOf(ViewColumn(title.id, ColumnWidth.WIDE)), saved.columns)
        assertEquals(listOf(SortKey(title.id, descending = true)), saved.sort)
        assertEquals(viewId, views.currentViewId(id))
    }

    @Test
    fun deletedFieldsDropOutOfViewsAndLastViewStays() = runBlocking {
        val id = repository.create("Books")
        val title = repository.addField(id, "Title", FieldType.STRING)
        val pages = repository.addField(id, "Pages", FieldType.INTEGER)
        val first = views.views(id, "All fields").single()
        views.save(
            id,
            first.copy(columns = listOf(ViewColumn(title.id), ViewColumn(pages.id)), sort = listOf(SortKey(pages.id)))
        )

        repository.deleteField(id, pages.id)

        val view = views.views(id, "x").single()
        assertEquals(listOf(ViewColumn(title.id)), view.columns)
        assertEquals(emptyList<SortKey>(), view.sort)
        assertTrue(runCatching { views.delete(id, view.id) }.isFailure)
    }

    @Test
    fun recordsSortByViewKeysWithEmptyValuesLast() = runBlocking {
        val id = repository.create("Books")
        val title = repository.addField(id, "Title", FieldType.STRING)
        val pages = repository.addField(id, "Pages", FieldType.INTEGER)
        val tags = repository.addField(id, "Tags", FieldType.LIST, ListProps(listOf("x"), multiple = true))
        fun add(t: String?, p: Long?, vararg tag: String) =
            runBlocking { records.insert(id, mapOf(title.id to t, pages.id to p), mapOf(tags.id to tag.toList())) }
        val dune = add("Dune", 412, "sf")
        val emma = add("emma", 9, "classic", "romance")
        val blank = add(null, null)
        val anathem = add("Anathem", 9, "sf")
        val titles = { sort: List<SortKey> -> runBlocking { records.records(id, sort).map { it.id } } }

        assertEquals(listOf(anathem, dune, emma, blank), titles(listOf(SortKey(title.id))))
        assertEquals(listOf(emma, dune, anathem, blank), titles(listOf(SortKey(title.id, descending = true))))
        // Case-sensitive: capitals before lower case.
        assertEquals(listOf(anathem, dune, emma, blank), titles(listOf(SortKey(title.id, caseSensitive = true))))
        // Numbers numerically; ties broken by the next key, then oldest first.
        assertEquals(listOf(anathem, emma, dune, blank), titles(listOf(SortKey(pages.id), SortKey(title.id))))
        assertEquals(listOf(emma, anathem, dune, blank), titles(listOf(SortKey(pages.id))))
        // Multiple choices by their joined text.
        assertEquals(listOf(emma, dune, anathem, blank), titles(listOf(SortKey(tags.id))))
        assertEquals(listOf(dune, emma, blank, anathem), titles(emptyList()))
    }
}
