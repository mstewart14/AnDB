package ca.stewartclan.andb.ui.common

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reading and writing files the user picked with the system file dialogs, which need no permission. */
class FileAccess(private val resolver: ContentResolver) {
    suspend fun writeText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        // "wt": replace the file's contents rather than writing over the start of them.
        val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException("Can't write $uri")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        val stream = resolver.openInputStream(uri) ?: throw IOException("Can't read $uri")
        stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
