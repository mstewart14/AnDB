package ca.stewartclan.andb.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldTypeTest {
    @Test
    fun codesAreUnique() {
        assertEquals(FieldType.entries.size, FieldType.entries.map { it.code }.toSet().size)
    }

    @Test
    fun fromCodeRoundTrips() {
        FieldType.entries.forEach { assertEquals(it, FieldType.fromCode(it.code)) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun fromCodeRejectsUnknown() {
        FieldType.fromCode("nope")
    }

    @Test
    fun keepsValuesOnlyWhenStorageMatchesOrThereWereNone() {
        assertTrue(FieldType.STRING.keepsValuesAs(FieldType.EMAIL))
        assertTrue(FieldType.INTEGER.keepsValuesAs(FieldType.RATING))
        assertTrue(FieldType.SECTION.keepsValuesAs(FieldType.FLOAT))
        assertFalse(FieldType.STRING.keepsValuesAs(FieldType.INTEGER))
        assertFalse(FieldType.FLOAT.keepsValuesAs(FieldType.SECTION))
        assertFalse(FieldType.STRING.keepsValuesAs(FieldType.ID))
        assertTrue(FieldType.SECTION.keepsValuesAs(FieldType.ID))
        assertTrue(FieldType.ID.keepsValuesAs(FieldType.ID))
    }
}
