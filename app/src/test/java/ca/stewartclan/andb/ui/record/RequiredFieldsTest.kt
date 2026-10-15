package ca.stewartclan.andb.ui.record

import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.FieldType
import ca.stewartclan.andb.data.ListProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequiredFieldsTest {
    private val title = FieldDef(1, 0, "Title", FieldType.STRING, required = true)
    private val pages = FieldDef(2, 1, "Pages", FieldType.INTEGER, required = true)
    private val rating = FieldDef(3, 2, "Rating", FieldType.RATING, required = true)
    private val tags = FieldDef(4, 3, "Tags", FieldType.LIST, ListProps(listOf("a"), multiple = true), required = true)
    private val note = FieldDef(5, 4, "Note", FieldType.NOTE)
    private val read = FieldDef(6, 5, "Read", FieldType.BOOLEAN)
    private val no = FieldDef(7, 6, "No.", FieldType.ID)
    private val fields = listOf(title, pages, rating, tags, note, read, no)

    @Test
    fun emptyOrIncompleteRequiredFieldsAreMissing() {
        val drafts = mapOf(title.id to "  ", pages.id to "-", rating.id to "0", no.id to "1")
        assertEquals(listOf(title, pages, rating, tags), RequiredFields.missing(fields, drafts, emptyMap()))
    }

    @Test
    fun filledRequiredFieldsAreNotMissing() {
        val drafts = mapOf(title.id to "Dune", pages.id to "412", rating.id to "4")
        val chosen = mapOf(tags.id to listOf("a"))
        assertEquals(emptyList<FieldDef>(), RequiredFields.missing(fields, drafts, chosen))
    }

    @Test
    fun blankIgnoresIdsButNotCheckboxesOrOptionalFields() {
        assertTrue(RequiredFields.isBlank(fields, mapOf(no.id to "1", read.id to "0"), emptyMap()))
        assertFalse(RequiredFields.isBlank(fields, mapOf(read.id to "1"), emptyMap()))
        assertFalse(RequiredFields.isBlank(fields, mapOf(note.id to "x"), emptyMap()))
        assertFalse(RequiredFields.isBlank(fields, emptyMap(), mapOf(tags.id to listOf("a"))))
    }
}
