package ca.stewartclan.andb.ui.chooser

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.stewartclan.andb.AnDbApplication
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.DatabaseSummary
import ca.stewartclan.andb.ui.common.AndbFileProvider
import ca.stewartclan.andb.ui.common.ShareFiles

/** Exporting a database to an `.andb` file, sharing one with another app, and importing one. */
class TransferActions(
    val onExport: (DatabaseSummary) -> Unit,
    val onShare: (DatabaseSummary) -> Unit,
    val onImport: () -> Unit
)

/** `.andb` files have no registered type, so they're saved as generic files; shared ones say what they are. */
private const val SAVE_TYPE = "application/octet-stream"

/**
 * Export (choose design only or with records, then where to save "<name>.andb") and import (choose
 * a file) through the system file dialogs, saying in [snackbarHostState] what was done.
 */
@Composable
fun rememberTransferActions(viewModel: ChooserViewModel, snackbarHostState: SnackbarHostState): TransferActions {
    val context = LocalContext.current
    val files = remember(context) { (context.applicationContext as AnDbApplication).files }
    val databases by viewModel.databases.collectAsStateWithLifecycle()
    // The database whose export (or share) dialog is open, then the one (and whether with records) being saved.
    var choosingId by rememberSaveable { mutableStateOf<String?>(null) }
    var sharing by rememberSaveable { mutableStateOf(false) }
    var savingId by rememberSaveable { mutableStateOf<String?>(null) }
    var savingRecords by rememberSaveable { mutableStateOf(false) }

    val saveTo = rememberLauncherForActivityResult(CreateDocument(SAVE_TYPE)) { uri ->
        val db = databases?.find { it.id == savingId }
        if (uri != null && db != null) viewModel.export(db, savingRecords) { files.writeBytes(uri, it) }
        savingId = null
    }
    val openFrom = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) viewModel.import { files.readBytes(uri) }
    }
    databases?.find { it.id == choosingId }?.let { db ->
        val shareTitle = stringResource(R.string.chooser_share_with)
        ExportDialog(
            name = db.name,
            sharing = sharing,
            onConfirm = { withRecords ->
                choosingId = null
                if (sharing) {
                    viewModel.share(db, withRecords) {
                        ShareFiles.share(context, "${db.name}.andb", AndbFileProvider.ANDB_TYPE, it, shareTitle)
                    }
                } else {
                    savingId = db.id
                    savingRecords = withRecords
                    saveTo.launch("${db.name}.andb")
                }
            },
            onDismiss = { choosingId = null }
        )
    }
    TransferNotices(viewModel, snackbarHostState)
    return TransferActions(
        onExport = {
            sharing = false
            choosingId = it.id
        },
        onShare = {
            sharing = true
            choosingId = it.id
        },
        onImport = { openFrom.launch(arrayOf("*/*")) }
    )
}

@Composable
private fun TransferNotices(viewModel: ChooserViewModel, snackbarHostState: SnackbarHostState) {
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val message = when (val n = notice) {
        is TransferNotice.Exported -> stringResource(R.string.chooser_exported, n.name)
        is TransferNotice.Imported -> stringResource(R.string.chooser_imported, n.name)
        null -> null
    }
    LaunchedEffect(message) {
        if (message != null) {
            viewModel.noticeShown()
            snackbarHostState.showSnackbar(message)
        }
    }
}

/** Chooses whether exporting (or [sharing]) database [name] includes its records; [onConfirm] gets the choice. */
@Composable
private fun ExportDialog(
    name: String,
    sharing: Boolean,
    onConfirm: (withRecords: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var withRecords by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (sharing) R.string.chooser_share_title else R.string.chooser_export_title, name))
        },
        text = {
            Column {
                Text(
                    stringResource(if (sharing) R.string.chooser_share_explained else R.string.chooser_export_explained)
                )
                CopyChoice(
                    stringResource(R.string.chooser_duplicate_design_only),
                    stringResource(R.string.chooser_duplicate_design_only_explained),
                    selected = !withRecords,
                    onSelect = { withRecords = false }
                )
                CopyChoice(
                    stringResource(R.string.chooser_duplicate_with_records),
                    stringResource(R.string.chooser_duplicate_with_records_explained),
                    selected = withRecords,
                    onSelect = { withRecords = true }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(withRecords) }) {
                Text(stringResource(if (sharing) R.string.action_share else R.string.action_export))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/**
 * Offers to add the database in file [uri], opened or shared into AnDB from another app. Adding
 * imports it (or says it isn't a database); either way [onDone] is called.
 */
@Composable
fun IncomingFileDialog(uri: Uri, viewModel: ChooserViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val files = remember(context) { (context.applicationContext as AnDbApplication).files }
    val name by produceState<String?>(null, uri) { value = files.displayName(uri) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.chooser_incoming_title)) },
        text = {
            Text(
                name?.let { stringResource(R.string.chooser_incoming_named, it) }
                    ?: stringResource(R.string.chooser_incoming)
            )
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.import { files.readBytes(uri) }
                onDone()
            }) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.action_cancel)) } }
    )
}
