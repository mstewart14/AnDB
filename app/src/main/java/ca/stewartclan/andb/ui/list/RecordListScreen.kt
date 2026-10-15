package ca.stewartclan.andb.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.ListView
import ca.stewartclan.andb.ui.common.ErrorSnackbar
import ca.stewartclan.andb.ui.record.NEW_RECORD_ID
import kotlinx.serialization.Serializable

/** Navigation route for a database's records (Pilot-DB's list view). */
@Serializable
data class RecordListRoute(val databaseId: String)

/** View id meaning "a new view" for the view editor. Stored views' ids start at 1. */
const val NEW_VIEW_ID = 0L

/** What the list screen's controls do. */
class ListActions(
    val onBack: () -> Unit,
    val onOpen: (Long) -> Unit,
    val onNew: () -> Unit,
    val onEditDesign: () -> Unit,
    val onSort: (FieldDef) -> Unit,
    val views: ViewActions
)

/** Choosing, adding, editing and deleting list views. */
class ViewActions(
    val onSelect: (ListView) -> Unit,
    val onNew: () -> Unit,
    val onEdit: () -> Unit,
    val onDelete: () -> Unit
)

@Composable
fun RecordListScreen(
    onBack: () -> Unit,
    onOpenRecord: (databaseId: String, recordId: Long) -> Unit,
    onEditDesign: (databaseId: String) -> Unit,
    onEditView: (databaseId: String, viewId: Long) -> Unit,
    viewModel: RecordListViewModel = viewModel(factory = RecordListViewModel.Factory)
) {
    val database by viewModel.database.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    ErrorSnackbar(error, snackbarHostState, viewModel::errorShown)
    var confirmDeleteView by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        viewModel.reload()
        onPauseOrDispose {}
    }
    val db = viewModel.databaseId

    RecordListContent(
        title = database?.name.orEmpty(),
        state = state,
        snackbarHostState = snackbarHostState,
        actions = ListActions(
            onBack = onBack,
            onOpen = { onOpenRecord(db, it) },
            onNew = { onOpenRecord(db, NEW_RECORD_ID) },
            onEditDesign = { onEditDesign(db) },
            onSort = viewModel::sortBy,
            views = ViewActions(
                onSelect = viewModel::selectView,
                onNew = { onEditView(db, NEW_VIEW_ID) },
                onEdit = { state?.view?.let { onEditView(db, it.id) } },
                onDelete = { confirmDeleteView = true }
            )
        )
    )

    if (confirmDeleteView) {
        AlertDialog(
            onDismissRequest = { confirmDeleteView = false },
            title = { Text(stringResource(R.string.view_delete_title, state?.view?.name.orEmpty())) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteView()
                    confirmDeleteView = false
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteView = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
fun RecordListContent(
    title: String,
    state: ListUiState?,
    snackbarHostState: SnackbarHostState,
    actions: ListActions,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = { ListTopBar(title, state, actions) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = actions.onNew) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.list_new_record))
            }
        }
    ) { innerPadding ->
        when {
            state == null -> Unit
            state.rows.isEmpty() -> EmptyList(state.hasFields, Modifier.padding(innerPadding))
            else -> RecordTable(
                columns = state.columns,
                rows = state.rows,
                sort = state.view.sort,
                onSort = actions.onSort,
                onOpen = actions.onOpen,
                contentPadding = innerPadding
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListTopBar(title: String, state: ListUiState?, actions: ListActions) {
    TopAppBar(
        title = {
            Column {
                Text(title)
                if (state != null) ViewPicker(state, actions)
            }
        },
        navigationIcon = {
            IconButton(onClick = actions.onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        },
        actions = { ListMenu(canDeleteView = (state?.views?.size ?: 0) > 1, actions) }
    )
}

/** The current view's name; tapping it lists the views to switch to, and view actions. */
@Composable
private fun ViewPicker(state: ListUiState, actions: ListActions) {
    var open by remember { mutableStateOf(false) }
    val pick = { action: () -> Unit ->
        open = false
        action()
    }
    Box {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { open = true }) {
            Text(state.view.name, style = MaterialTheme.typography.bodyMedium)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.view_choose))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.views.forEach { view ->
                DropdownMenuItem(
                    text = { Text(view.name) },
                    leadingIcon = if (view.id == state.view.id) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = { pick { actions.views.onSelect(view) } }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(text = {
                Text(stringResource(R.string.view_edit))
            }, onClick = { pick(actions.views.onEdit) })
            DropdownMenuItem(text = {
                Text(stringResource(R.string.view_new))
            }, onClick = { pick(actions.views.onNew) })
        }
    }
}

@Composable
private fun ListMenu(canDeleteView: Boolean, actions: ListActions) {
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
                    actions.onEditDesign()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.view_delete)) },
                enabled = canDeleteView,
                onClick = {
                    open = false
                    actions.views.onDelete()
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
