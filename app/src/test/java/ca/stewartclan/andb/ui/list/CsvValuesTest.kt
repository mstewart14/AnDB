package ca.stewartclan.andb.ui.list

import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.FieldType
import ca.stewartclan.andb.data.IdKind
import ca.stewartclan.andb.data.IdProps
import ca.stewartclan.andb.data.LinkProps
import ca.stewartclan.andb.data.LinkSource
import ca.stewartclan.andb.data.LinkValue
import ca.stewartclan.andb.data.ListProps
import ca.stewartclan.andb.data.Record
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvValuesTest {
    private fun field(
        type: FieldType,
        props: ca.stewartclan.andb.data.FieldProps = ca.stewartclan.andb.data.FieldProps.default(type)
    ) = FieldDef(1, 0, "F", type, props)

    private fun import(type: FieldType, text: String) = CsvValues.import(field(type), text)

    @Test
    fun exportUsesPlainForms() {
        val record = Record(
            1,
            "2026-10-15T08:00:00Z",
            "2026-10-16T08:00:00Z",
            mapOf(1L to 1L, 2L to "2026-03-01", 3L to 4L),
            mapOf(4L to listOf("sf", "signed"))
        )
        assertEquals("yes", CsvValues.export(FieldDef(1, 0, "Read", FieldType.BOOLEAN), record, "✓"))
        assertEquals("2026-03-01", CsvValues.export(FieldDef(2, 0, "D", FieldType.DATE), record, "Mar 1, 2026"))
        assertEquals("4", CsvValues.export(FieldDef(3, 0, "R", FieldType.RATING), record, "★★★★☆"))
        assertEquals(
            "sf; signed",
            CsvValues.export(FieldDef(4, 0, "T", FieldType.LIST, ListProps(listOf("sf"), true)), record, "sf, signed")
        )
        assertEquals("2026-10-15T08:00:00Z", CsvValues.export(FieldDef(5, 0, "C", FieldType.CREATED), record, "x"))
        assertEquals("Dune", CsvValues.export(FieldDef(6, 0, "Title", FieldType.STRING), record, "Dune"))
    }

    @Test
    fun importParsesByType() {
        assertEquals(CellValue.Value(412L), import(FieldType.INTEGER, " 412 "))
        assertEquals(CellValue.Value(2.5), import(FieldType.FLOAT, "2,5"))
        assertEquals(CellValue.Value("2026-03-01"), import(FieldType.DATE, "2026-03-01"))
        assertEquals(CellValue.Value("09:05"), import(FieldType.TIME, "9:05".padStart(5, '0')))
        assertEquals(CellValue.Value(1L), import(FieldType.BOOLEAN, "Yes"))
        assertEquals(CellValue.Value(0L), import(FieldType.BOOLEAN, ""))
        assertEquals(CellValue.Value(null), import(FieldType.INTEGER, ""))
        assertEquals(CellValue.Value("Dune"), import(FieldType.STRING, "Dune"))
    }

    @Test
    fun importRejectsWhatDoesNotFit() {
        assertEquals(CellValue.Invalid, import(FieldType.INTEGER, "abc"))
        assertEquals(CellValue.Invalid, import(FieldType.DATE, "March 1"))
        assertEquals(CellValue.Invalid, import(FieldType.RATING, "7"))
        assertEquals(CellValue.Invalid, import(FieldType.BOOLEAN, "maybe"))
    }

    @Test
    fun importsChoicesAndIds() {
        val tags = field(FieldType.LIST, ListProps(listOf("a"), multiple = true))
        assertEquals(CellValue.Choices(listOf("sf", "signed")), CsvValues.import(tags, "sf; signed;; sf"))
        val no = field(FieldType.ID, IdProps(prefix = "INV-", padding = 4))
        assertEquals(CellValue.Value(42L), CsvValues.import(no, "INV-0042"))
        assertEquals(CellValue.Invalid, CsvValues.import(no, "X-1"))
        assertEquals(
            CellValue.Value("K7QX-29MD"),
            CsvValues.import(field(FieldType.ID, IdProps(IdKind.SHORT_CODE)), "K7QX-29MD")
        )
    }

    @Test
    fun recordLinksMatchOneTitleExactly() {
        val author = field(FieldType.LINK, LinkProps(LinkSource.RECORD, "authors"))
        val titles = mapOf(1L to "Le Guin", 2L to "Banks", 3L to "banks")
        assertEquals(
            CellValue.Value(LinkValue("andb://authors/1", "Le Guin", null)),
            CsvValues.import(author, "le guin", titles)
        )
        assertEquals(CellValue.Invalid, CsvValues.import(author, "Banks", titles))
        assertEquals(CellValue.Invalid, CsvValues.import(author, "Atwood", titles))
    }

    @Test
    fun whatCanBeImported() {
        assertTrue(field(FieldType.STRING).canImport)
        assertTrue(field(FieldType.LINK, LinkProps(LinkSource.RECORD, "a")).canImport)
        assertFalse(field(FieldType.LINK, LinkProps(LinkSource.PHOTO)).canImport)
        assertFalse(field(FieldType.LINKED).canImport)
        assertFalse(field(FieldType.CREATED).canImport)
    }
}
