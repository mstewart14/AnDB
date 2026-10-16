package ca.stewartclan.andb

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import ca.stewartclan.andb.ui.AnDbNavHost
import ca.stewartclan.andb.ui.theme.AnDbTheme

class MainActivity : ComponentActivity() {
    /** A database file opened or shared into AnDB, until the user has added it or said no. */
    private var incoming by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incoming =
            if (savedInstanceState == null) fileIn(intent) else savedInstanceState.getString(KEY_INCOMING)?.toUri()
        enableEdgeToEdge()
        setContent {
            AnDbTheme {
                AnDbNavHost(incoming = incoming, onIncomingHandled = { incoming = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        fileIn(intent)?.let { incoming = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        incoming?.let { outState.putString(KEY_INCOMING, it.toString()) }
    }

    /**
     * The file [intent] opens (View) or shares (Send) with AnDB, if any. Only content uris, which
     * the sending app grants: another app can name any uri, and AnDB could read some it shouldn't.
     */
    private fun fileIn(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        else -> null
    }?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }

    private companion object {
        const val KEY_INCOMING = "incoming"
    }
}
