package ca.stewartclan.andb.data

import org.junit.Assert.assertEquals
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
}
