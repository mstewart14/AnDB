package ca.stewartclan.andb.ui.common

import android.content.ContentResolver
import android.net.Uri

/**
 * Whether this is a content uri, the only kind AnDB reads or opens from a link: the pickers give
 * those, while a link in a database from someone else could name a file path in AnDB's own storage.
 */
val Uri.isContent: Boolean get() = scheme == ContentResolver.SCHEME_CONTENT
