package ca.stewartclan.andb.ui.list

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FindQueryTest {
    private val record = mapOf(1L to "The Left Hand of Darkness", 2L to "Ursula K. Le Guin", 3L to "INV-0042")

    @Test
    fun blankMatchesEverything() {
        assertTrue(FindQuery("  ").matches(record))
    }

    @Test
    fun containsIgnoringCaseByDefault() {
        assertTrue(FindQuery("darkness").matches(record))
        assertTrue(FindQuery("le g").matches(record))
        assertFalse(FindQuery("dispossessed").matches(record))
    }

    @Test
    fun caseSensitive() {
        assertFalse(FindQuery("darkness", caseSensitive = true).matches(record))
        assertTrue(FindQuery("Darkness", caseSensitive = true).matches(record))
    }

    @Test
    fun wholeWord() {
        assertTrue(FindQuery("hand", wholeWord = true).matches(record))
        assertFalse(FindQuery("han", wholeWord = true).matches(record))
        assertTrue(FindQuery("0042", wholeWord = true).matches(record))
        assertFalse(FindQuery("004", wholeWord = true).matches(record))
    }

    @Test
    fun oneFieldOnly() {
        assertTrue(FindQuery("guin", fieldId = 2).matches(record))
        assertFalse(FindQuery("guin", fieldId = 1).matches(record))
        assertFalse(FindQuery("guin", fieldId = 99).matches(record))
    }

    @Test
    fun specialCharactersAreLiteral() {
        assertTrue(FindQuery("K.").matches(record))
        assertFalse(FindQuery("K*").matches(record))
        assertTrue(FindQuery("inv-00").matches(record))
    }
}
