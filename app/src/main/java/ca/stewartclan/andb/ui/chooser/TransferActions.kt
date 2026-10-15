package ca.stewartclan.andb.ui.chooser

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.stewartclan.andb.AnDbApplication
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.DatabaseSummary

/** Exporting a database to an `.andb` file, and importing one. */
class TransferActions(val onExport: (DatabaseSummary) -> Unit, val onImport: () -> Unit)

/** `.andb` files have no registered type, so they're saved as, and opened from, any file. */
private const val ANDB_TYPE = "application/octet-stream"

/**
 * Export (choose design only or with records, then where to save "<name>.andb") and import (choose
 * a file) through the system file dialogs, saying in [snackbarHostState] what was done.
 */
@Composable
fun rememberTransferActions(viewModel: ChooserViewModel, snackbarHostState: SnackbarHostState): TransferActions {
    val context = LocalContext.current
    val files = remember(context) { (context.applicationContext as AnDbApplication).files }
    val databases by viewModel.databases.collectAsStateWithLifecycle()
    // The database whose export dialog is open, then the one (and whether with records) being saved.
    var choosingId by rememberSaveable { mutableStateOf<String?>(null) }
    var savingId by rememberSaveable { mutableStateOf<String?>(null) }
    var savingRecords by rememberSaveable { mutableStateOf(false) }

    val saveTo = rememberLauncherForActivityResult(CreateDocument(ANDB_TYPE)) { uri ->
        val db = databases?.find { it.id == savingId }
        if (uri != null && db != null) viewModel.export(db, savingRecords) { files.writeBytes(uri, it) }
        savingId = null
    }
    val openFrom = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) viewModel.import { files.readBytes(uri) }
    }
    databases?.find { it.id == choosingId }?.let { db ->
        ExportDialog(
            name = db.name,
            onConfirm = { withRecords ->
                choosingId = null
                savingId = db.id
                savingRecords = withRecords
                saveTo.launch("${db.name}.andb")
            },
            onDismiss = { choosingId = null }
        )
    }
    TransferNotices(viewModel, snackbarHostState)
    return TransferActions(onExport = { choosingId = it.id }, onImport = { openFrom.launch(arrayOf("*/*")) })
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

/** Chooses whether exporting database [name] includes its records; [onConfirm] gets the choice. */
@Composable
private fun ExportDialog(name: String, onConfirm: (withRecords: Boolean) -> Unit, onDismiss: () -> Unit) {
    var withRecords by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chooser_export_title, name)) },
        text = {
            Column {
                Text(stringResource(R.string.chooser_export_explained))
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
            TextButton(onClick = { onConfirm(withRecords) }) { Text(stringResource(R.string.action_export)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
