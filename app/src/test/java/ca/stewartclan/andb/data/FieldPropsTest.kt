package ca.stewartclan.andb.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FieldPropsTest {
    @Test
    fun idPropsRoundTrip() {
        val props = IdProps(IdKind.INCREMENT, start = 100, prefix = "INV-", padding = 4)
        assertEquals(props, FieldProps.decode(FieldType.ID, FieldProps.encode(props)))
    }

    @Test
    fun defaultsAreOmittedAndRestored() {
        assertEquals("{}", FieldProps.encode(IdProps()))
        assertEquals(IdProps(), FieldProps.decode(FieldType.ID, "{}"))
    }

    @Test
    fun kindIsStoredByStableName() {
        assertEquals("""{"kind":"uuid7"}""", FieldProps.encode(IdProps(IdKind.UUID_TIME)))
    }

    @Test
    fun unknownKeysAreIgnored() {
        assertEquals(IdProps(IdKind.SHORT_CODE), FieldProps.decode(FieldType.ID, """{"kind":"code","future":1}"""))
    }

    @Test
    fun typesWithoutSettingsUseNoProps() {
        assertEquals(NoProps, FieldProps.decode(FieldType.STRING, "{}"))
        assertEquals("{}", FieldProps.encode(NoProps))
    }

    @Test
    fun formatAddsPrefixAndPadding() {
        assertEquals("INV-0042", IdProps(prefix = "INV-", padding = 4).format(42))
        assertEquals("12345", IdProps(padding = 3).format(12345))
        assertEquals("7", IdProps().format(7))
    }

    @Test
    fun listPropsRoundTrip() {
        val fixed = ListProps(listOf("Fiction", "History"), multiple = true)
        val lookup = ListProps(lookup = Lookup("db-id", 7))
        assertEquals(fixed, FieldProps.decode(FieldType.LIST, FieldProps.encode(fixed)))
        assertEquals(lookup, FieldProps.decode(FieldType.LIST, FieldProps.encode(lookup)))
        assertEquals(ListProps(), FieldProps.decode(FieldType.LIST, "{}"))
    }

    @Test
    fun linkPropsRoundTrip() {
        assertEquals(LinkProps(), FieldProps.decode(FieldType.LINK, FieldProps.encode(LinkProps())))
        assertEquals(LinkProps(LinkSource.PHOTO), FieldProps.decode(FieldType.LINK, """{"source":"photo"}"""))
        assertEquals(LinkProps(LinkSource.CONTACT), FieldProps.decode(FieldType.LINK, """{"source":"contact"}"""))
        assertEquals("""{"source":"file"}""", FieldProps.encode(LinkProps(LinkSource.FILE)))
    }

    @Test
    fun recordRefRoundTripsAndRejectsOtherUris() {
        val ref = RecordRef("0b1c-db", 42)
        assertEquals("andb://0b1c-db/42", ref.uri)
        assertEquals(ref, RecordRef.parse(ref.uri))
        assertEquals(null, RecordRef.parse("content://media/42"))
        assertEquals(null, RecordRef.parse("andb://db/x"))
        assertEquals(null, RecordRef.parse("andb://db/1/2"))
    }

    @Test
    fun recordLinkPropsRoundTrip() {
        val props = LinkProps(LinkSource.RECORD, "db", 7)
        assertEquals(props, FieldProps.decode(FieldType.LINK, FieldProps.encode(props)))
    }
}
