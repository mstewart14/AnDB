package ca.stewartclan.andb.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldDefTest {
    private fun field(type: FieldType, props: FieldProps = FieldProps.default(type)) = FieldDef(1, 0, "F", type, props)

    private val single = field(FieldType.LIST, ListProps(listOf("a")))
    private val multi = field(FieldType.LIST, ListProps(listOf("a"), multiple = true))

    @Test
    fun multiListHasNoColumnButHasValues() {
        assertEquals(Storage.NONE, multi.storage)
        assertTrue(multi.hasValues)
        assertEquals(Storage.TEXT, single.storage)
        assertFalse(field(FieldType.SECTION).hasValues)
    }

    @Test
    fun keepsValuesWhenStorageMatchesOrThereWereNone() {
        assertTrue(field(FieldType.STRING).keepsValuesAs(field(FieldType.EMAIL)))
        assertTrue(field(FieldType.INTEGER).keepsValuesAs(field(FieldType.RATING)))
        assertTrue(field(FieldType.SECTION).keepsValuesAs(field(FieldType.FLOAT)))
        assertFalse(field(FieldType.STRING).keepsValuesAs(field(FieldType.INTEGER)))
        assertFalse(field(FieldType.FLOAT).keepsValuesAs(field(FieldType.SECTION)))
    }

    @Test
    fun becomingAnIdReplacesValues() {
        assertFalse(field(FieldType.STRING).keepsValuesAs(field(FieldType.ID)))
        assertTrue(field(FieldType.SECTION).keepsValuesAs(field(FieldType.ID)))
        assertTrue(field(FieldType.ID).keepsValuesAs(field(FieldType.ID)))
    }

    @Test
    fun multipleChoicesKeepValuesOnlyWhileMultiple() {
        assertTrue(single.keepsValuesAs(multi))
        assertTrue(field(FieldType.STRING).keepsValuesAs(multi))
        assertTrue(multi.keepsValuesAs(multi))
        assertFalse(multi.keepsValuesAs(single))
        assertFalse(multi.keepsValuesAs(field(FieldType.STRING)))
    }

    @Test
    fun linksKeepValuesOnlyAsLinks() {
        val link = field(FieldType.LINK)
        assertFalse(field(FieldType.STRING).keepsValuesAs(link))
        assertFalse(link.keepsValuesAs(field(FieldType.STRING)))
        assertTrue(link.keepsValuesAs(link))
        assertTrue(field(FieldType.SECTION).keepsValuesAs(link))
    }

    @Test
    fun linkValuesCompareByContent() {
        assertEquals(LinkValue("u", "a.jpg", byteArrayOf(1, 2)), LinkValue("u", "a.jpg", byteArrayOf(1, 2)))
        assertNotEquals(LinkValue("u", "a.jpg", byteArrayOf(1, 2)), LinkValue("u", "a.jpg", byteArrayOf(1)))
        assertTrue(RequiredRule.hasValue(field(FieldType.LINK), LinkValue("u", "a.jpg", null), emptyList()))
        assertFalse(RequiredRule.hasValue(field(FieldType.LINK), null, emptyList()))
    }
}
