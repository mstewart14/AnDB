package ca.stewartclan.andb.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.stewartclan.andb.R
import ca.stewartclan.andb.ui.common.ErrorSnackbar
import ca.stewartclan.andb.ui.record.NEW_RECORD_ID
import ca.stewartclan.andb.ui.theme.AnDbTheme
import kotlinx.serialization.Serializable

/** Navigation route for a database's records (Pilot-DB's list view). */
@Serializable
data class RecordListRoute(val databaseId: String)

@Composable
fun RecordListScreen(
    onBack: () -> Unit,
    onOpenRecord: (databaseId: String, recordId: Long) -> Unit,
    onEditDesign: (databaseId: String) -> Unit,
    viewModel: RecordListViewModel = viewModel(factory = RecordListViewModel.Factory)
) {
    val database by viewModel.database.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    ErrorSnackbar(error, snackbarHostState, viewModel::errorShown)
    LifecycleResumeEffect(Unit) {
        viewModel.reload()
        onPauseOrDispose {}
    }

    RecordListContent(
        title = database?.name.orEmpty(),
        hasFields = (database?.fieldCount ?: 0) > 0,
        rows = rows,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onOpen = { onOpenRecord(viewModel.databaseId, it) },
        onNew = { onOpenRecord(viewModel.databaseId, NEW_RECORD_ID) },
        onEditDesign = { onEditDesign(viewModel.databaseId) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordListContent(
    title: String,
    hasFields: Boolean,
    rows: List<RecordRow>?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onEditDesign: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = { ListMenu(onEditDesign) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNew) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.list_new_record))
            }
        }
    ) { innerPadding ->
        when {
            rows == null -> Unit
            rows.isEmpty() -> EmptyList(hasFields, Modifier.padding(innerPadding))
            else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = innerPadding) {
                items(rows, key = { it.id }) { row ->
                    ListItem(
                        modifier = Modifier.clickable { onOpen(row.id) },
                        headlineContent = {
                            Text(
                                row.values.firstOrNull() ?: stringResource(R.string.list_empty_record),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = row.values.drop(1).takeIf { it.isNotEmpty() }?.let { rest ->
                            { Text(rest.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ListMenu(onEditDesign: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.list_edit_design)) },
                onClick = {
                    open = false
                    onEditDesign()
                }
            )
        }
    }
}

@Composable
private fun EmptyList(hasFields: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.list_empty_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(if (hasFields) R.string.list_empty_body else R.string.list_empty_no_fields),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RecordListContentPreview() {
    AnDbTheme(dynamicColor = false) {
        RecordListContent(
            title = "Books",
            hasFields = true,
            rows = listOf(
                RecordRow(1, listOf("Dune", "Frank Herbert", "★★★★★")),
                RecordRow(2, listOf("The Dispossessed", "Ursula K. Le Guin")),
                RecordRow(3, emptyList())
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onOpen = {},
            onNew = {},
            onEditDesign = {}
        )
    }
}
