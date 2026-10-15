package ca.stewartclan.andb.data

import java.security.SecureRandom
import java.util.UUID

/** Generates the non-sequential ID kinds. Increments come from a stored counter instead. */
class IdGenerator(
    private val random: SecureRandom = SecureRandom(),
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    fun uuidRandom(): String = UUID.randomUUID().toString()

    /** RFC 9562 version 7: 48-bit Unix milliseconds, then random bits, so it sorts by creation time. */
    fun uuidTime(): String {
        val timestamp = nowMillis() and TIMESTAMP_MASK
        val msb = (timestamp shl TIMESTAMP_SHIFT) or VERSION_7 or (random.nextLong() and RAND_A_MASK)
        val lsb = (random.nextLong() and RAND_B_MASK) or VARIANT_RFC
        return UUID(msb, lsb).toString()
    }

    /** Eight characters as `XXXX-XXXX` from an alphabet without look-alikes (0/O, 1/I/L). */
    fun shortCode(): String = buildString {
        repeat(CODE_LENGTH) { i ->
            if (i == CODE_LENGTH / 2) append('-')
            append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)])
        }
    }

    companion object {
        const val CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
        private const val CODE_LENGTH = 8
        private const val TIMESTAMP_MASK = 0xFFFF_FFFF_FFFFL
        private const val TIMESTAMP_SHIFT = 16
        private const val VERSION_7 = 0x7000L
        private const val RAND_A_MASK = 0x0FFFL
        private const val RAND_B_MASK = 0x3FFF_FFFF_FFFF_FFFFL
        private const val VARIANT_RFC = Long.MIN_VALUE // Top two bits 10.
    }
}
