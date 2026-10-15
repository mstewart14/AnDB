package ca.stewartclan.andb.ui.common

import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.FieldType
import ca.stewartclan.andb.data.ListProps
import ca.stewartclan.andb.data.Record
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordTitlesTest {
    private val aroma = FieldDef(1, 0, "Aroma", FieldType.STRING)
    private val category = FieldDef(2, 1, "Category", FieldType.STRING)
    private val type = FieldDef(3, 2, "Type", FieldType.LIST, ListProps(listOf("Primary", "Secondary", "Faults")))
    private val notes = FieldDef(4, 3, "Notes", FieldType.LIST, ListProps(multiple = true))

    private fun record(id: Long, values: Map<Long, Any?>, choices: Map<Long, List<String>> = emptyMap()) =
        Record(id, "2026-10-15T12:00:00Z", "2026-10-15T12:00:00Z", values, choices)

    private val records = listOf(
        record(1, mapOf(aroma.id to "Lemon", category.id to "Citrus", type.id to "Primary")),
        record(2, mapOf(aroma.id to "Lime", category.id to "Citrus", type.id to "Primary")),
        record(3, mapOf(aroma.id to "Butter", category.id to "Microbial", type.id to "Secondary")),
        record(4, mapOf(aroma.id to "Wet Dog", category.id to " ", type.id to "Faults"), mapOf(4L to listOf("a", "b")))
    )

    @Test
    fun `a field with a few shared values is a facet, its blanks left out`() {
        val facet = RecordTitles.facet(category, records)!!
        assertEquals(listOf("Citrus", "Microbial"), facet.values)
        assertEquals(emptyList<String>(), facet.byRecord[4])
    }

    @Test
    fun `a List facet keeps the List's order`() {
        assertEquals(listOf("Primary", "Secondary", "Faults"), RecordTitles.facet(type, records)!!.values)
    }

    @Test
    fun `a field with a different value on every record is no facet`() {
        assertNull(RecordTitles.facet(aroma, records))
    }

    @Test
    fun `a field with a single value is no facet`() {
        assertNull(RecordTitles.facet(category, records.take(2)))
    }

    @Test
    fun `a multiple-choice List gives each record its choices`() {
        val more = records + record(5, emptyMap(), mapOf(notes.id to listOf("b")))
        val facet = RecordTitles.facet(notes, more)!!
        assertEquals(listOf("a", "b"), facet.values)
        assertEquals(listOf("a", "b"), facet.byRecord[4])
    }

    @Test
    fun `too many values is no facet`() {
        val many = (1L..(RecordTitles.MAX_FACET_VALUES + 2L)).flatMap { n ->
            listOf(record(n * 2, mapOf(category.id to "c$n")), record(n * 2 + 1, mapOf(category.id to "c$n")))
        }
        assertNull(RecordTitles.facet(category, many))
    }
}
