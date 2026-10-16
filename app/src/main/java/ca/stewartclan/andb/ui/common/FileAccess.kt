package ca.stewartclan.andb.ui.common

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reading and writing files the user picked with the system file dialogs, which need no permission. */
class FileAccess(private val resolver: ContentResolver) {
    suspend fun writeText(uri: Uri, text: String) = writeBytes(uri, text.toByteArray(Charsets.UTF_8))

    suspend fun readText(uri: Uri): String = readBytes(uri).toString(Charsets.UTF_8)

    suspend fun writeBytes(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        // "wt": replace the file's contents rather than writing over the start of them.
        val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException("Can't write $uri")
        stream.use { it.write(bytes) }
    }

    /** The name [uri]'s file goes by, if its provider says. */
    suspend fun displayName(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: SecurityException) {
            null
        }
    }

    suspend fun readBytes(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val stream = resolver.openInputStream(uri) ?: throw IOException("Can't read $uri")
        stream.use { it.readBytes() }
    }
}
