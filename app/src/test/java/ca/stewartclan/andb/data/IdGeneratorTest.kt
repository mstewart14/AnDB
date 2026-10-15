package ca.stewartclan.andb.data

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdGeneratorTest {
    @Test
    fun uuidRandomIsVersion4() {
        assertEquals(4, UUID.fromString(IdGenerator().uuidRandom()).version())
    }

    @Test
    fun uuidTimeIsVersion7WithRfcVariantAndTimestamp() {
        val millis = 1_760_000_000_000L
        val uuid = UUID.fromString(IdGenerator(nowMillis = { millis }).uuidTime())
        assertEquals(7, uuid.version())
        assertEquals(2, uuid.variant())
        assertEquals(millis, uuid.mostSignificantBits ushr 16)
    }

    @Test
    fun uuidTimeSortsByCreationTime() {
        var now = 1_760_000_000_000L
        val generator = IdGenerator(nowMillis = { now++ })
        val ids = List(50) { generator.uuidTime() }
        assertEquals(ids.sorted(), ids)
    }

    @Test
    fun shortCodeIsTwoGroupsOfFourFromAlphabet() {
        val generator = IdGenerator()
        repeat(200) {
            val code = generator.shortCode()
            assertTrue(code, Regex("[${IdGenerator.CODE_ALPHABET}]{4}-[${IdGenerator.CODE_ALPHABET}]{4}").matches(code))
        }
    }

    @Test
    fun alphabetHasNoLookAlikes() {
        "01OIL".forEach { assertTrue(it !in IdGenerator.CODE_ALPHABET) }
    }
}
