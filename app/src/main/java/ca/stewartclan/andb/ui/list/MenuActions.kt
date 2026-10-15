package ca.stewartclan.andb.ui.list

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.stewartclan.andb.AnDbApplication
import ca.stewartclan.andb.R

/** The list menu's actions besides views and filters. */
class MenuActions(val onEditDesign: () -> Unit, val onExportCsv: () -> Unit, val onImportCsv: () -> Unit)

/** File types offered when choosing a CSV file; some apps label CSV as plain text or Excel. */
private val CSV_TYPES = arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/csv")

/**
 * The list menu's actions, with the system file dialogs for CSV export (save as "<name>.csv") and
 * import ([onImportCsv] gets the chosen file's uri). Says how many records an export wrote.
 */
@Composable
fun rememberMenuActions(
    viewModel: RecordListViewModel,
    databaseName: String,
    snackbarHostState: SnackbarHostState,
    onEditDesign: () -> Unit,
    onImportCsv: (String) -> Unit
): MenuActions {
    val context = LocalContext.current
    val files = remember(context) { (context.applicationContext as AnDbApplication).files }
    val exportTo = rememberLauncherForActivityResult(CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.exportCsv { files.writeText(uri, it) }
    }
    val importFrom = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) onImportCsv(uri.toString())
    }
    val exported by viewModel.exported.collectAsStateWithLifecycle()
    val message = exported?.let { pluralStringResource(R.plurals.csv_exported, it, it) }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.exportedShown()
        }
    }
    return MenuActions(
        onEditDesign = onEditDesign,
        onExportCsv = { exportTo.launch("$databaseName.csv") },
        onImportCsv = { importFrom.launch(CSV_TYPES) }
    )
}
