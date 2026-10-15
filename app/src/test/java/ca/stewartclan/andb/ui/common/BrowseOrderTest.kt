package ca.stewartclan.andb.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowseOrderTest {
    @Test
    fun withoutAListOrderRecordsGoOldestFirst() {
        assertEquals(listOf(1L, 2L, 3L), BrowseOrder().arrange("db", listOf(1L, 2L, 3L)))
    }

    @Test
    fun followsTheListLeavingOutDeletedAndAppendingNewRecords() {
        val order = BrowseOrder()
        order["db"] = listOf(3L, 1L, 4L, 2L)
        assertEquals(listOf(3L, 1L, 2L, 5L), order.arrange("db", listOf(1L, 2L, 3L, 5L)))
        assertEquals(listOf(7L), order.arrange("other", listOf(7L)))
    }
}
