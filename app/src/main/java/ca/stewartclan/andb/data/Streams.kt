package ca.stewartclan.andb.data

import java.io.InputStream
import java.io.OutputStream

/**
 * Copies this stream into [output], throwing what [tooLarge] makes once more than [max] bytes have
 * come: files from other apps can be any size, and must not fill the memory or the phone.
 */
internal fun InputStream.copyAtMost(output: OutputStream, max: Long, tooLarge: () -> Exception) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) return
        total += read
        if (total > max) throw tooLarge()
        output.write(buffer, 0, read)
    }
}
