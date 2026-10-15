package ca.stewartclan.andb.ui.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.FieldType
import ca.stewartclan.andb.data.ListProps
import ca.stewartclan.andb.ui.common.ErrorSnackbar
import ca.stewartclan.andb.ui.common.ReorderState
import ca.stewartclan.andb.ui.common.dragToReorder
import ca.stewartclan.andb.ui.theme.AnDbTheme
import kotlinx.serialization.Serializable

/** Navigation route for a database's design view (its field list). */
@Serializable
data class DesignRoute(val databaseId: String)

/** Edit-dialog id meaning "add a new field" (`null` means no dialog). Field ids are rowids, never 0. */
private const val NEW_FIELD_ID = 0L

@Composable
fun DesignScreen(onBack: () -> Unit, viewModel: DesignViewModel = viewModel(factory = DesignViewModel.Factory)) {
    val database by viewModel.database.collectAsStateWithLifecycle()
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val databases by viewModel.databases.collectAsStateWithLifecycle()
    val linkedTargets by viewModel.linkedTargets.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    ErrorSnackbar(error, snackbarHostState, viewModel::errorShown)
    // Dialogs remember the field id (or NEW_FIELD_ID), so they survive rotation.
    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val recordCount = database?.recordCount ?: 0
    val lookupContext = rememberLookupContext(viewModel)

    DesignContent(
        databaseName = database?.name.orEmpty(),
        fields = fields,
        names = SummaryNames(databases.orEmpty().associate { it.id to it.name }, linkedTargets),
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onAdd = { editId = NEW_FIELD_ID },
        onEdit = { editId = it.id },
        onMove = viewModel::moveField,
        onMoveTo = viewModel::moveFieldTo,
        onDelete = { deleteId = it.id }
    )

    val editing = fields?.find { it.id == editId }
    if (editId == NEW_FIELD_ID || editing != null) {
        FieldDialog(
            title = stringResource(if (editing == null) R.string.design_add_field else R.string.design_edit_field),
            confirmLabel = stringResource(if (editing == null) R.string.action_add else R.string.action_save),
            field = editing,
            recordCount = recordCount,
            lookup = lookupContext,
            check = { viewModel.checkName(it, editing = editing) },
            onConfirm = { entered ->
                if (editing == null) viewModel.addField(entered) else viewModel.updateField(editing, entered)
                editId = null
            },
            onDismiss = { editId = null }
        )
    }
    fields?.find { it.id == deleteId }?.let { field ->
        DeleteFieldDialog(
            field = field,
            recordCount = recordCount,
            onConfirm = {
                viewModel.deleteField(field)
                deleteId = null
            },
            onDismiss = { deleteId = null }
        )
    }
}

@Composable
private fun rememberLookupContext(viewModel: DesignViewModel): LookupContext {
    val databases by viewModel.databases.collectAsStateWithLifecycle()
    val info by viewModel.lookupInfo.collectAsStateWithLifecycle()
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    return LookupContext(fields.orEmpty(), databases.orEmpty(), info, viewModel::loadLookup, viewModel.databaseId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignContent(
    databaseName: String,
    fields: List<FieldDef>?,
    names: SummaryNames,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (FieldDef) -> Unit,
    onMove: (FieldDef, Int) -> Unit,
    onMoveTo: (FieldDef, Int) -> Unit,
    onDelete: (FieldDef) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(databaseName)
                        Text(stringResource(R.string.design_subtitle), style = MaterialTheme.typography.bodyMedium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.design_add_field))
            }
        }
    ) { innerPadding ->
        when {
            fields == null -> Unit
            fields.isEmpty() -> EmptyDesign(Modifier.padding(innerPadding))
            else -> FieldList(fields, names, innerPadding, onEdit, onMove, onMoveTo, onDelete)
        }
    }
}

@Composable
private fun FieldList(
    fields: List<FieldDef>,
    names: SummaryNames,
    contentPadding: PaddingValues,
    onEdit: (FieldDef) -> Unit,
    onMove: (FieldDef, Int) -> Unit,
    onMoveTo: (FieldDef, Int) -> Unit,
    onDelete: (FieldDef) -> Unit
) {
    // Order shown while dragging; reset whenever the saved order arrives.
    var order by remember(fields) { mutableStateOf(fields) }
    val savedFields by rememberUpdatedState(fields)
    val listState = rememberLazyListState()
    val reorder = remember(listState) { ReorderState(listState) }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        itemsIndexed(order, key = { _, field -> field.id }) { index, field ->
            val dragging = reorder.draggedKey == field.id
            val rowModifier = if (dragging) {
                Modifier
                    .zIndex(1f)
                    .graphicsLayer {
                        translationY = reorder.translationOf(field.id)
                        shadowElevation = DRAG_ELEVATION.toPx()
                    }
            } else {
                Modifier.animateItem()
            }
            val dragHandle = Modifier.dragToReorder(
                state = reorder,
                key = field.id,
                onMoveTo = { to ->
                    val from = order.indexOfFirst { it.id == field.id }
                    if (from != to) order = order.toMutableList().apply { add(to, removeAt(from)) }
                },
                onDrop = {
                    val to = order.indexOfFirst { it.id == field.id }
                    if (to != savedFields.indexOfFirst { it.id == field.id }) onMoveTo(field, to)
                },
                onCancel = { order = savedFields }
            )
            Column(rowModifier) {
                FieldRow(
                    field = field,
                    summary = fieldSummary(field, fields, names),
                    dragHandle = {
                        Icon(
                            Icons.Filled.Menu,
                            contentDescription = stringResource(R.string.design_drag_handle),
                            modifier = dragHandle
                        )
                    },
                    canMoveUp = index > 0,
                    canMoveDown = index < order.lastIndex,
                    onEdit = { onEdit(field) },
                    onMove = { by -> onMove(field, by) },
                    onDelete = { onDelete(field) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EmptyDesign(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.design_empty_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.design_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun FieldRow(
    field: FieldDef,
    summary: String,
    dragHandle: @Composable () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        modifier = modifier.clickable(onClick = onEdit),
        leadingContent = dragHandle,
        headlineContent = { Text(field.name) },
        supportingContent = { Text(summary) },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_move_up)) },
                        enabled = canMoveUp,
                        onClick = {
                            menuOpen = false
                            onMove(-1)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_move_down)) },
                        enabled = canMoveDown,
                        onClick = {
                            menuOpen = false
                            onMove(1)
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

@Preview(showBackground = true)
@Composable
private fun DesignContentPreview() {
    AnDbTheme(dynamicColor = false) {
        DesignContent(
            databaseName = "Books",
            fields = listOf(
                FieldDef(1, 0, "Title", FieldType.STRING),
                FieldDef(2, 1, "Author", FieldType.STRING),
                FieldDef(3, 2, "Read", FieldType.BOOLEAN),
                FieldDef(4, 3, "Rating", FieldType.RATING),
                FieldDef(5, 4, "Genre", FieldType.LIST, ListProps(listOf("Fiction", "History", "Science"), true))
            ),
            names = SummaryNames(emptyMap(), emptyMap()),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onAdd = {},
            onEdit = {},
            onMove = { _, _ -> },
            onMoveTo = { _, _ -> },
            onDelete = {}
        )
    }
}

private val DRAG_ELEVATION = 8.dp
