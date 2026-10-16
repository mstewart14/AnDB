package ca.stewartclan.andb.ui.common

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.IOException

// Content uris from the system pickers and other apps. These block, so call them off the main thread.

private const val TAG = "ContentUris"

/**
 * Whether this is a content uri, the only kind AnDB reads or opens from a link: the pickers give
 * those, while a link in a database from someone else could name a file path in AnDB's own storage.
 */
val Uri.isContent: Boolean get() = scheme == ContentResolver.SCHEME_CONTENT

/** The name [uri]'s file goes by, if its provider says. */
fun ContentResolver.displayName(uri: Uri): String? = try {
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
} catch (_: SecurityException) {
    null
}

/**
 * Keeps read access to picked [uri] after AnDB closes. If the picker didn't allow that, it's still
 * readable until then, and the link's snapshot keeps the record showing something after.
 */
fun ContentResolver.keepReadAccess(uri: Uri) {
    try {
        takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (e: SecurityException) {
        Log.w(TAG, "No lasting access to $uri", e)
    }
}

/** Whether content [uri] can be read now. */
fun ContentResolver.canRead(uri: Uri): Boolean = uri.isContent &&
    try {
        openInputStream(uri)?.use { true } ?: false
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }
