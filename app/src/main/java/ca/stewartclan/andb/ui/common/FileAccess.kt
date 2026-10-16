package ca.stewartclan.andb.ui.common

import android.content.ContentResolver
import android.net.Uri
import ca.stewartclan.andb.data.copyAtMost
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reading and writing files the user picked with the system file dialogs, which need no permission. */
class FileAccess(private val resolver: ContentResolver) {
    suspend fun writeText(uri: Uri, text: String) = writeBytes(uri, text.toByteArray(Charsets.UTF_8))

    /** The text of file [uri], read as UTF-8; refused if it's larger than [MAX_TEXT_BYTES]. */
    suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = ByteArrayOutputStream()
        open(uri).use { input ->
            input.copyAtMost(bytes, MAX_TEXT_BYTES) { IOException("That file is too large (over 32 MB)") }
        }
        bytes.toString(Charsets.UTF_8.name())
    }

    suspend fun writeBytes(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        // "wt": replace the file's contents rather than writing over the start of them.
        val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException("Can't write $uri")
        stream.use { it.write(bytes) }
    }

    /** The name [uri]'s file goes by, if its provider says. */
    suspend fun displayName(uri: Uri): String? = withContext(Dispatchers.IO) { resolver.displayName(uri) }

    /** Opens file [uri] for reading; the caller closes it. */
    suspend fun open(uri: Uri): InputStream = withContext(Dispatchers.IO) {
        resolver.openInputStream(uri) ?: throw IOException("Can't read $uri")
    }

    private companion object {
        /** Largest text file read (a CSV file to import), well past any real one. */
        const val MAX_TEXT_BYTES = 32L * 1024 * 1024
    }
}
