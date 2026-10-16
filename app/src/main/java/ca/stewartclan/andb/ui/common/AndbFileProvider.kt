package ca.stewartclan.andb.ui.common

import android.net.Uri
import androidx.core.content.FileProvider

/**
 * AnDB's FileProvider, which says what `.andb` files are: Android doesn't know the extension and
 * would call them generic binary files, so apps receiving one couldn't tell to offer AnDB.
 */
class AndbFileProvider : FileProvider() {
    override fun getType(uri: Uri): String? =
        if (uri.lastPathSegment?.endsWith(".andb", ignoreCase = true) == true) ANDB_TYPE else super.getType(uri)

    companion object {
        /** The type of `.andb` files, which AnDB opens. */
        const val ANDB_TYPE = "application/vnd.andb"
    }
}
