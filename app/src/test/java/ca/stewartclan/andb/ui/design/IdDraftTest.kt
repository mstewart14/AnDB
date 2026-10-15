package ca.stewartclan.andb.ui.design

import ca.stewartclan.andb.data.IdKind
import ca.stewartclan.andb.data.IdProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdDraftTest {
    private val valid = IdDraft(IdKind.INCREMENT, start = "100", prefix = "INV-", padding = "4")

    @Test
    fun validIncrementBecomesProps() {
        assertEquals(IdProps(IdKind.INCREMENT, 100, "INV-", 4), valid.toProps())
    }

    @Test
    fun roundTripsThroughProps() {
        val props = IdProps(IdKind.INCREMENT, 5, "A", 2)
        assertEquals(props, IdDraft.of(props).toProps())
    }

    @Test
    fun rejectsBadIncrementSettings() {
        assertNull(valid.copy(start = "").toProps())
        assertNull(valid.copy(start = "-1").toProps())
        assertNull(valid.copy(start = "abc").toProps())
        assertNull(valid.copy(padding = "${IdProps.MAX_PADDING + 1}").toProps())
        assertNull(valid.copy(prefix = "x".repeat(IdProps.MAX_PREFIX_LENGTH + 1)).toProps())
    }

    @Test
    fun otherKindsIgnoreIncrementSettings() {
        assertEquals(IdProps(IdKind.SHORT_CODE), valid.copy(kind = IdKind.SHORT_CODE, start = "junk").toProps())
    }
}
