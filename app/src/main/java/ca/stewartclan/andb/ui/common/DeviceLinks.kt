package ca.stewartclan.andb.ui.common

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract.Contacts
import android.util.Log
import androidx.core.net.toUri
import ca.stewartclan.andb.data.LinkSource
import ca.stewartclan.andb.data.LinkValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Contacts and files linked from records, chosen with the system pickers, so AnDB needs no
 * permission for either and never changes them: their snapshot (the name), whether they can still
 * be reached, and opening them in their own apps.
 */
class DeviceLinks(private val resolver: ContentResolver) {

    /**
     * The link to contact [uri] from the contact picker: its lookup uri, which still finds the
     * contact after its row id changes in a sync, and its name. Null if it can't be read.
     */
    suspend fun contact(uri: Uri): LinkValue? = withContext(Dispatchers.IO) {
        try {
            // The picker grants reading this one contact, for now.
            resolver.query(uri, arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (!c.moveToFirst()) return@use null
                    val lookup = Contacts.getLookupUri(c.getLong(0), c.getString(1)) ?: uri
                    LinkValue(lookup.toString(), c.getString(2).orEmpty(), null)
                }
        } catch (e: SecurityException) {
            Log.w(TAG, "Can't read contact $uri", e)
            null
        }
    }

    /** The link to file [uri] from the file picker, keeping read access to it, named as the file. */
    suspend fun file(uri: Uri): LinkValue = withContext(Dispatchers.IO) {
        resolver.keepReadAccess(uri)
        LinkValue(uri.toString(), resolver.displayName(uri) ?: uri.lastPathSegment.orEmpty(), null)
    }

    /**
     * Whether linked file [uri] can still be read. Contacts aren't checked: that would need
     * permission to read all contacts, and the Contacts app says so itself if one is gone.
     */
    suspend fun isAvailable(source: LinkSource, uri: String): Boolean = withContext(Dispatchers.IO) {
        source != LinkSource.FILE || resolver.canRead(uri.toUri())
    }

    /** Opens [link] in the app for it (Contacts, or one for the file's type); false if none can. */
    fun open(context: Context, source: LinkSource, link: LinkValue): Boolean {
        val uri = link.uri.toUri()
        if (!uri.isContent) return false
        val intent = Intent(Intent.ACTION_VIEW).apply {
            if (source == LinkSource.FILE) {
                setDataAndType(uri, resolver.getType(uri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                data = uri
            }
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Can't open ${link.uri}", e)
            false
        }
    }

    private companion object {
        const val TAG = "DeviceLinks"
    }
}
