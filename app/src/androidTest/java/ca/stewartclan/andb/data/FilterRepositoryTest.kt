package ca.stewartclan.andb.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilterRepositoryTest {
    private lateinit var dir: File
    private lateinit var repository: DatabaseRepository
    private lateinit var filters: FilterRepository

    @Before
    fun setUp() {
        dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "filter-test")
        dir.deleteRecursively()
        repository = DatabaseRepository(dir)
        filters = FilterRepository(repository)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun saveUpdateAndApply() = runBlocking {
        val id = repository.create("Books")
        val title = repository.addField(id, "Title", FieldType.STRING)
        val tags = repository.addField(id, "Tags", FieldType.LIST, ListProps(listOf("a", "b"), multiple = true))
        val conditions = listOf(
            Condition(title.id, Op.CONTAINS, "dune"),
            Condition(tags.id, Op.ANY_OF, choices = listOf("a", "b"))
        )

        val filterId = filters.save(id, Filter(0, 0, "SF", matchAll = false, conditions))
        filters.save(id, filters.filters(id).single().copy(name = "Science fiction"))
        filters.setCurrentFilter(id, filterId)

        val saved = filters.filters(id).single()
        assertEquals("Science fiction", saved.name)
        assertEquals(false, saved.matchAll)
        assertEquals(conditions, saved.conditions)
        assertEquals(filterId, filters.currentFilterId(id))

        filters.setCurrentFilter(id, null)
        assertNull(filters.currentFilterId(id))
    }

    @Test
    fun deletingAppliedFilterShowsAllRecords() = runBlocking {
        val id = repository.create("Books")
        val filterId = filters.save(id, Filter(0, 0, "F", true, emptyList()))
        filters.setCurrentFilter(id, filterId)

        filters.delete(id, filterId)

        assertEquals(emptyList<Filter>(), filters.filters(id))
        assertNull(filters.currentFilterId(id))
    }

    @Test
    fun conditionsOnDeletedFieldsDropOut() = runBlocking {
        val id = repository.create("Books")
        val title = repository.addField(id, "Title", FieldType.STRING)
        val pages = repository.addField(id, "Pages", FieldType.INTEGER)
        filters.save(
            id,
            Filter(0, 0, "F", true, listOf(Condition(title.id, Op.IS, "x"), Condition(pages.id, Op.GREATER, "1")))
        )

        repository.deleteField(id, pages.id)

        assertEquals(listOf(Condition(title.id, Op.IS, "x")), filters.filters(id).single().conditions)
    }
}
