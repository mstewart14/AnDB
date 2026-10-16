package ca.stewartclan.andb.ui.common

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Files handed to another app with the system share sheet, through AnDB's FileProvider. */
object ShareFiles {
    /** Characters a file name can't hold on some systems. */
    private val UNSAFE = Regex("""[/\\:*?"<>|]""")

    /**
     * Opens the share sheet to send [bytes] as a file named [name] (of type [type]). Only the latest
     * shared file is kept, in the cache, and the app chosen may only read that one.
     */
    suspend fun share(context: Context, name: String, type: String, bytes: ByteArray, title: String) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shared").apply {
                deleteRecursively()
                mkdirs()
            }
            File(dir, name.replace(UNSAFE, "_")).apply { writeBytes(bytes) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            this.type = type
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title))
    }
}
