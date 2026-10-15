package ca.stewartclan.andb.ui.design

import ca.stewartclan.andb.data.ListProps
import ca.stewartclan.andb.data.Lookup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListDraftTest {
    @Test
    fun itemsAreTrimmedAndATrailingBlankIsIgnored() {
        val draft = ListDraft(items = listOf(" Fiction ", "History", ""), multiple = true)
        assertEquals(ListProps(listOf("Fiction", "History"), multiple = true), draft.toProps())
        assertEquals(emptySet<Int>(), draft.badItems)
    }

    @Test
    fun blankOrRepeatedItemsAreBad() {
        val draft = ListDraft(items = listOf("Fiction", " ", "fiction", "History"))
        assertEquals(setOf(1, 2), draft.badItems)
        assertNull(draft.toProps())
    }

    @Test
    fun needsAtLeastOneItem() {
        assertNull(ListDraft().toProps())
        assertNull(ListDraft(items = emptyList()).toProps())
    }

    @Test
    fun lookupNeedsDatabaseAndField() {
        assertNull(ListDraft(useLookup = true).toProps())
        assertNull(ListDraft(useLookup = true, lookupDatabaseId = "db").toProps())
        assertEquals(
            ListProps(lookup = Lookup("db", 3)),
            ListDraft(useLookup = true, lookupDatabaseId = "db", lookupFieldId = 3).toProps()
        )
    }

    @Test
    fun switchingSourceKeepsTheOther() {
        val draft = ListDraft(items = listOf("A"), useLookup = true, lookupDatabaseId = "db", lookupFieldId = 3)
        assertEquals(ListProps(listOf("A")), draft.copy(useLookup = false).toProps())
    }

    @Test
    fun itemEditing() {
        val draft = ListDraft(items = listOf("A", "B", "C"))
        assertEquals(listOf("C", "A", "B"), draft.withItemMoved(2, 0).items)
        assertEquals(listOf("A", "C"), draft.withoutItem(1).items)
        assertEquals(listOf("A", "X", "C"), draft.withItem(1, "X").items)
    }

    @Test
    fun roundTripsThroughProps() {
        val props = ListProps(listOf("A", "B"), multiple = true)
        assertEquals(props, ListDraft.of(props).toProps())
    }
}
