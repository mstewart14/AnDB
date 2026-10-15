package ca.stewartclan.andb.ui.record

import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.FieldType
import ca.stewartclan.andb.data.IdProps
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FieldValuesTest {
    private val default = Locale.getDefault()

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(default)
    }

    private fun field(type: FieldType) = FieldDef(1, 0, "F", type)

    @Test
    fun draftRoundTrips() {
        assertEquals("", FieldValues.toDraft(null))
        assertEquals("42", FieldValues.toDraft(42L))
        assertEquals("2.5", FieldValues.toDraft(2.5))
        assertEquals("3", FieldValues.toDraft(3.0))
        assertEquals(42L, FieldValues.fromDraft(field(FieldType.INTEGER), "42"))
        assertEquals(2.5, FieldValues.fromDraft(field(FieldType.FLOAT), "2,5"))
        assertEquals("Dune", FieldValues.fromDraft(field(FieldType.STRING), " Dune "))
    }

    @Test
    fun incompleteOrBlankDraftsStoreNull() {
        assertNull(FieldValues.fromDraft(field(FieldType.INTEGER), "-"))
        assertNull(FieldValues.fromDraft(field(FieldType.FLOAT), "."))
        assertNull(FieldValues.fromDraft(field(FieldType.STRING), "  "))
    }

    @Test
    fun checkboxesStoreOneOrZero() {
        assertEquals(1L, FieldValues.fromDraft(field(FieldType.BOOLEAN), "1"))
        assertEquals(0L, FieldValues.fromDraft(field(FieldType.BOOLEAN), ""))
    }

    @Test
    fun numberFilter() {
        assertEquals("-12", FieldValues.filterNumber("-1a2", decimal = false))
        assertEquals("12", FieldValues.filterNumber("1-2.", decimal = false))
        assertEquals("1.53", FieldValues.filterNumber("1.5.3", decimal = true))
        assertEquals("1,53", FieldValues.filterNumber("1,5.3", decimal = true))
    }

    @Test
    fun display() {
        assertEquals("✓", FieldValues.display(field(FieldType.BOOLEAN), 1L))
        assertEquals("", FieldValues.display(field(FieldType.BOOLEAN), 0L))
        assertEquals("★★★☆☆", FieldValues.display(field(FieldType.RATING), 3L))
        assertEquals("Oct 15, 2026", FieldValues.display(field(FieldType.DATE), "2026-10-15"))
        assertEquals("not a date", FieldValues.display(field(FieldType.DATE), "not a date"))
        assertEquals(
            "INV-007",
            FieldValues.display(FieldDef(1, 0, "No", FieldType.ID, IdProps(prefix = "INV-", padding = 3)), 7L)
        )
        assertEquals("", FieldValues.display(field(FieldType.STRING), null))
    }
}
