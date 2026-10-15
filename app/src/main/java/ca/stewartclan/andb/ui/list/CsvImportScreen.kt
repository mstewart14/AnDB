package ca.stewartclan.andb.ui.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.stewartclan.andb.R
import ca.stewartclan.andb.ui.common.ErrorSnackbar

/** How many sample values each column shows, and how many unreadable values the report lists. */
private const val SAMPLES = 3
private const val BAD_EXAMPLES = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportScreen(
    onDone: () -> Unit,
    viewModel: CsvImportViewModel = viewModel(factory = CsvImportViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    ErrorSnackbar(error, snackbarHostState, viewModel::errorShown)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.csv_import_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_cancel)
                        )
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::import, enabled = state?.canImport == true) {
                        Text(stringResource(R.string.csv_import_go))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        state?.let { ImportContent(it, viewModel, Modifier.padding(innerPadding)) }
    }
    state?.report?.let { ImportReportDialog(it, onDone) }
}

@Composable
private fun ImportContent(state: CsvImportState, viewModel: CsvImportViewModel, modifier: Modifier) {
    LazyColumn(contentPadding = PaddingValues(16.dp), modifier = modifier.fillMaxSize()) {
        item {
            if (state.importing) LinearProgressIndicator(Modifier.padding(bottom = 8.dp))
            Text(
                pluralStringResource(R.plurals.csv_import_rows, state.dataRows.size, state.dataRows.size),
                style = MaterialTheme.typography.bodyMedium
            )
            SwitchRow(
                stringResource(R.string.csv_import_header),
                state.firstRowIsHeader,
                viewModel::setFirstRowIsHeader
            )
            if (state.mapsId) {
                SwitchRow(
                    stringResource(R.string.csv_import_keep_ids),
                    state.keepIds,
                    viewModel::setKeepIds
                )
            }
            SectionHeading(stringResource(R.string.csv_import_columns))
        }
        items(state.columnCount) { column -> ColumnMapping(state, column, viewModel) }
    }
}

/** One CSV column: its name, a few of its values, and where it goes. */
@Composable
private fun ColumnMapping(state: CsvImportState, column: Int, viewModel: CsvImportViewModel) {
    val samples = state.dataRows.mapNotNull {
        it.getOrNull(column)?.trim()?.takeIf { v -> v.isNotEmpty() }
    }.take(SAMPLES)
    val skip = stringResource(R.string.csv_import_skip)
    val newField = stringResource(R.string.csv_import_new_field)
    val options = listOf<Pair<ColumnTarget, String>>(ColumnTarget.Skip to skip, ColumnTarget.NewField to newField) +
        state.fields.map { ColumnTarget.Field(it.id) to it.name }
    val current = options.find { it.first == state.targets.getOrNull(column) }?.second ?: skip
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(state.names[column], fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    samples.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            MenuButton("→ $current", options, showAddIcon = false) { viewModel.setTarget(column, it) }
        }
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ImportReportDialog(report: ImportReport, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(pluralStringResource(R.plurals.csv_import_done, report.imported, report.imported)) },
        text = {
            Column {
                if (report.missingRequired > 0) {
                    Text(
                        pluralStringResource(
                            R.plurals.csv_import_missing,
                            report.missingRequired,
                            report.missingRequired
                        )
                    )
                }
                if (report.duplicateIds > 0) {
                    Text(
                        pluralStringResource(
                            R.plurals.csv_import_duplicate_ids,
                            report.duplicateIds,
                            report.duplicateIds
                        )
                    )
                }
                if (report.badValues.isNotEmpty()) {
                    Text(
                        pluralStringResource(
                            R.plurals.csv_import_bad_values,
                            report.badValues.size,
                            report.badValues.size
                        )
                    )
                    report.badValues.take(BAD_EXAMPLES).forEach {
                        Text(
                            stringResource(R.string.csv_import_bad_value, it.row, it.column, it.text),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.action_close)) } }
    )
}
