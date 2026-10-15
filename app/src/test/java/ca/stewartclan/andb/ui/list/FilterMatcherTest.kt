package ca.stewartclan.andb.ui.list

import ca.stewartclan.andb.data.Condition
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.FieldType
import ca.stewartclan.andb.data.Filter
import ca.stewartclan.andb.data.IdProps
import ca.stewartclan.andb.data.ListProps
import ca.stewartclan.andb.data.Op
import ca.stewartclan.andb.data.Record
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FilterMatcherTest {
    private val title = FieldDef(1, 0, "Title", FieldType.STRING)
    private val pages = FieldDef(2, 1, "Pages", FieldType.INTEGER)
    private val read = FieldDef(3, 2, "Read", FieldType.BOOLEAN)
    private val finished = FieldDef(4, 3, "Finished", FieldType.DATE)
    private val tags = FieldDef(5, 4, "Tags", FieldType.LIST, ListProps(listOf("sf", "classic", "signed"), true))
    private val no = FieldDef(6, 5, "No.", FieldType.ID, IdProps(prefix = "B-"))
    private val created = FieldDef(7, 6, "Created", FieldType.CREATED)
    private val fields = listOf(title, pages, read, finished, tags, no, created).associateBy { it.id }

    private val dune = Record(
        1,
        "2026-10-15T12:00:00Z",
        "2026-10-15T12:00:00Z",
        mapOf(title.id to "Dune", pages.id to 412L, read.id to 1L, finished.id to "2026-03-01", no.id to 1L),
        mapOf(tags.id to listOf("sf", "signed"))
    )
    private val blank = Record(2, "2026-10-16T12:00:00Z", "2026-10-16T12:00:00Z", emptyMap(), emptyMap())

    private val zone = TimeZone.getDefault()

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(zone)
    }

    private fun test(vararg conditions: Condition, all: Boolean = true) =
        listOf(dune, blank).filter { FilterMatcher.matches(Filter(1, 0, "F", all, conditions.toList()), fields, it) }
            .map { it.id }

    @Test
    fun text() {
        assertEquals(listOf(1L), test(Condition(title.id, Op.CONTAINS, "UN")))
        assertEquals(listOf(2L), test(Condition(title.id, Op.NOT_CONTAINS, "un")))
        assertEquals(listOf(1L), test(Condition(title.id, Op.IS, "dune")))
        assertEquals(listOf(1L), test(Condition(title.id, Op.STARTS_WITH, "Du")))
        assertEquals(listOf(2L), test(Condition(title.id, Op.EMPTY)))
        assertEquals(listOf(1L), test(Condition(no.id, Op.IS, "1")))
    }

    @Test
    fun numbersAndIncrementIds() {
        assertEquals(listOf(1L), test(Condition(pages.id, Op.GREATER, "400")))
        assertEquals(emptyList<Long>(), test(Condition(pages.id, Op.LESS, "400")))
        assertEquals(listOf(1L), test(Condition(pages.id, Op.GREATER_OR_EQUAL, "412")))
        // An empty value is "not" anything, but never less or greater.
        assertEquals(listOf(2L), test(Condition(pages.id, Op.IS_NOT, "412")))
        assertEquals(listOf(2L), test(Condition(pages.id, Op.EMPTY)))
    }

    @Test
    fun checkboxes() {
        assertEquals(listOf(1L), test(Condition(read.id, Op.CHECKED)))
        assertEquals(listOf(2L), test(Condition(read.id, Op.UNCHECKED)))
    }

    @Test
    fun datesAndTimestamps() {
        assertEquals(listOf(1L), test(Condition(finished.id, Op.LESS, "2026-06-01")))
        assertEquals(emptyList<Long>(), test(Condition(finished.id, Op.GREATER, "2026-06-01")))
        assertEquals(listOf(2L), test(Condition(created.id, Op.GREATER, "2026-10-15")))
        assertEquals(listOf(1L), test(Condition(created.id, Op.IS, "2026-10-15")))
    }

    @Test
    fun choices() {
        assertEquals(listOf(1L), test(Condition(tags.id, Op.ANY_OF, choices = listOf("classic", "sf"))))
        assertEquals(emptyList<Long>(), test(Condition(tags.id, Op.ALL_OF, choices = listOf("classic", "sf"))))
        assertEquals(listOf(2L), test(Condition(tags.id, Op.NONE_OF, choices = listOf("sf"))))
        assertEquals(listOf(2L), test(Condition(tags.id, Op.EMPTY)))
    }

    @Test
    fun allOrAny() {
        val big = Condition(pages.id, Op.GREATER, "400")
        val empty = Condition(title.id, Op.EMPTY)
        assertEquals(emptyList<Long>(), test(big, empty, all = true))
        assertEquals(listOf(1L, 2L), test(big, empty, all = false))
    }

    @Test
    fun incompleteAndUnknownConditionsAreIgnored() {
        assertEquals(listOf(1L, 2L), test(Condition(pages.id, Op.GREATER, "lots")))
        assertEquals(listOf(1L, 2L), test(Condition(99, Op.CONTAINS, "x")))
        assertEquals(listOf(1L, 2L), test(Condition(tags.id, Op.ANY_OF)))
        // An operator that doesn't fit the field (e.g. left over from a type change) is ignored too.
        assertEquals(listOf(1L, 2L), test(Condition(read.id, Op.CONTAINS, "x")))
        assertTrue(FilterMatcher.isComplete(Condition(pages.id, Op.EMPTY), pages))
        assertFalse(FilterMatcher.isComplete(Condition(title.id, Op.IS, " "), title))
    }
}
