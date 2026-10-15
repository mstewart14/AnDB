package ca.stewartclan.andb.ui.chooser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.DatabaseSummary
import ca.stewartclan.andb.ui.theme.AnDbTheme
import kotlinx.serialization.Serializable

/** Navigation route for the database list (Pilot-DB's "chooser"). */
@Serializable
object ChooserRoute

@Composable
fun ChooserRoute(viewModel: ChooserViewModel = viewModel(factory = ChooserViewModel.Factory)) {
    val databases by viewModel.databases.collectAsStateWithLifecycle()
    // Dialogs remember the database id rather than the summary, so they survive rotation.
    var newOpen by rememberSaveable { mutableStateOf(false) }
    var renameId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }

    ChooserScreen(
        databases = databases,
        onNewDatabase = { newOpen = true },
        onRename = { renameId = it.id },
        onDelete = { deleteId = it.id }
    )

    if (newOpen) {
        NameDialog(
            title = stringResource(R.string.chooser_new_database),
            confirmLabel = stringResource(R.string.action_create),
            initialName = "",
            check = viewModel::checkName,
            onConfirm = {
                viewModel.create(it)
                newOpen = false
            },
            onDismiss = { newOpen = false }
        )
    }
    databases?.find { it.id == renameId }?.let { db ->
        NameDialog(
            title = stringResource(R.string.chooser_rename_database),
            confirmLabel = stringResource(R.string.action_rename),
            initialName = db.name,
            check = { viewModel.checkName(it, renaming = db) },
            onConfirm = {
                viewModel.rename(db, it)
                renameId = null
            },
            onDismiss = { renameId = null }
        )
    }
    databases?.find { it.id == deleteId }?.let { db ->
        DeleteDialog(
            database = db,
            onConfirm = {
                viewModel.delete(db)
                deleteId = null
            },
            onDismiss = { deleteId = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChooserScreen(
    databases: List<DatabaseSummary>?,
    onNewDatabase: () -> Unit,
    onRename: (DatabaseSummary) -> Unit,
    onDelete: (DatabaseSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewDatabase) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.chooser_new_database))
            }
        }
    ) { innerPadding ->
        when {
            databases == null -> Unit
            databases.isEmpty() -> EmptyChooser(Modifier.padding(innerPadding))
            else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = innerPadding) {
                items(databases, key = { it.id }) { db ->
                    DatabaseRow(db, onRename = { onRename(db) }, onDelete = { onDelete(db) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun EmptyChooser(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.chooser_empty_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.chooser_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun DatabaseRow(database: DatabaseSummary, onRename: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(database.name) },
        supportingContent = {
            Text(
                pluralStringResource(
                    R.plurals.chooser_record_count,
                    database.recordCount.toInt(),
                    database.recordCount
                ) + " · " +
                    pluralStringResource(
                        R.plurals.chooser_field_count,
                        database.fieldCount.toInt(),
                        database.fieldCount
                    )
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rename)) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
    )
}

@Composable
private fun NameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    check: (String) -> NameProblem,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialName, TextRange(0, initialName.length)))
    }
    val problem = check(value.text)
    // A blank name is only an error once the user has typed something and cleared it again.
    val showError = problem != NameProblem.OK && !(problem == NameProblem.BLANK && value.text.isEmpty())
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(R.string.chooser_name_label)) },
                singleLine = true,
                isError = showError,
                supportingText = if (showError) {
                    { Text(problemMessage(problem)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (problem == NameProblem.OK) onConfirm(value.text) }
                ),
                modifier = Modifier.focusRequester(focus)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.text) }, enabled = problem == NameProblem.OK) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun problemMessage(problem: NameProblem): String = when (problem) {
    NameProblem.BLANK -> stringResource(R.string.chooser_name_blank)
    NameProblem.TOO_LONG -> stringResource(R.string.chooser_name_too_long, DatabaseNameValidator.MAX_LENGTH)
    NameProblem.DUPLICATE -> stringResource(R.string.chooser_name_duplicate)
    NameProblem.OK -> ""
}

@Composable
private fun DeleteDialog(database: DatabaseSummary, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chooser_delete_title, database.name)) },
        text = {
            Text(
                pluralStringResource(
                    R.plurals.chooser_delete_body,
                    database.recordCount.toInt(),
                    database.recordCount
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

private val previewDatabases = listOf(
    DatabaseSummary("1", "Books", recordCount = 212, fieldCount = 7),
    DatabaseSummary("2", "Car maintenance", recordCount = 1, fieldCount = 5),
    DatabaseSummary("3", "Wine cellar", recordCount = 0, fieldCount = 0)
)

@Preview(showBackground = true)
@Composable
private fun ChooserScreenPreview() {
    AnDbTheme(dynamicColor = false) {
        ChooserScreen(databases = previewDatabases, onNewDatabase = {}, onRename = {}, onDelete = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun ChooserScreenEmptyPreview() {
    AnDbTheme(dynamicColor = false) {
        ChooserScreen(databases = emptyList(), onNewDatabase = {}, onRename = {}, onDelete = {})
    }
}
