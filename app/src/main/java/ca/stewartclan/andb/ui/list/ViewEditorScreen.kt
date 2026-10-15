package ca.stewartclan.andb.ui.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.ColumnWidth
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.ViewColumn
import ca.stewartclan.andb.ui.common.ErrorSnackbar
import ca.stewartclan.andb.ui.common.NameProblem
import ca.stewartclan.andb.ui.common.ReorderState
import ca.stewartclan.andb.ui.common.dragToReorder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewEditorScreen(
    onDone: () -> Unit,
    viewModel: ViewEditorViewModel = viewModel(factory = ViewEditorViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    ErrorSnackbar(error, snackbarHostState, viewModel::errorShown)
    LaunchedEffect(state?.saved) { if (state?.saved == true) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val isNew = state?.draft?.id == NEW_VIEW_ID
                    Text(stringResource(if (isNew) R.string.view_new else R.string.view_edit))
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_cancel)
                        )
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = state?.nameProblem == NameProblem.OK) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        state?.let { ViewEditorContent(it, viewModel, Modifier.padding(innerPadding)) }
    }
}

/** Items before the first column row in the editor's list: name, "show all" switch, heading. */
private const val ITEMS_BEFORE_COLUMNS = 3

@Composable
private fun ViewEditorContent(state: ViewEditorState, viewModel: ViewEditorViewModel, modifier: Modifier) {
    val draft = state.draft
    val listState = rememberLazyListState()
    val reorder = remember(listState) { ReorderState(listState) }
    LazyColumn(state = listState, contentPadding = PaddingValues(16.dp), modifier = modifier.fillMaxSize()) {
        item {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { name -> viewModel.edit { it.copy(name = name) } },
                label = { Text(stringResource(R.string.name_label)) },
                singleLine = true,
                isError = state.nameProblem != NameProblem.OK,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            SwitchRow(stringResource(R.string.view_show_all), draft.showAll) { on ->
                viewModel.edit { it.copy(showAll = on) }
            }
        }
        item { SectionHeading(stringResource(R.string.view_columns)) }
        if (!draft.showAll) columnItems(state, viewModel, reorder)
        item { SectionHeading(stringResource(R.string.view_sort)) }
        sortItems(state, viewModel)
    }
}

private fun LazyListScope.columnItems(state: ViewEditorState, viewModel: ViewEditorViewModel, reorder: ReorderState) {
    val draft = state.draft
    val byId = state.fields.associateBy { it.id }
    items(draft.columns, key = { column -> "column-${column.fieldId}" }) { column ->
        val field = byId[column.fieldId] ?: return@items
        val dragging = reorder.draggedKey == "column-${column.fieldId}"
        val rowModifier = if (dragging) {
            Modifier
                .zIndex(1f)
                .graphicsLayer { translationY = reorder.translationOf("column-${column.fieldId}") }
        } else {
            Modifier.animateItem()
        }
        // Looked up by field id, not index: the drag gesture keeps this lambda while the order changes.
        val moveTo = { to: Int ->
            viewModel.edit { draft ->
                val from = draft.columns.indexOfFirst { it.fieldId == column.fieldId }
                draft.copy(columns = draft.columns.moved(from, to))
            }
        }
        ColumnRow(
            field = field,
            column = column,
            handle = {
                Icon(
                    Icons.Filled.Menu,
                    contentDescription = stringResource(R.string.design_drag_handle),
                    modifier = Modifier.dragToReorder(
                        state = reorder,
                        key = "column-${column.fieldId}",
                        onMoveTo = { layoutIndex -> moveTo(layoutIndex - ITEMS_BEFORE_COLUMNS) },
                        onDrop = {},
                        onCancel = {}
                    ).padding(end = 12.dp)
                )
            },
            onWidth = { viewModel.setWidth(field.id, it) },
            onRemove = { viewModel.removeColumn(field.id) },
            modifier = rowModifier
        )
    }
    item {
        val unused = state.columnFields.filter { field -> draft.columns.none { it.fieldId == field.id } }
        MenuButton(
            stringResource(R.string.view_add_column),
            unused.map {
                it to it.name
            },
            onPick = viewModel::addColumn
        )
    }
}

@Composable
private fun ColumnRow(
    field: FieldDef,
    column: ViewColumn,
    handle: @Composable () -> Unit,
    onWidth: (ColumnWidth) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            handle()
            Text(field.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onRemove) { Text(stringResource(R.string.action_remove)) }
        }
        val widths = ColumnWidth.entries
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            widths.forEachIndexed { i, width ->
                SegmentedButton(
                    selected = column.width == width,
                    onClick = { onWidth(width) },
                    shape = SegmentedButtonDefaults.itemShape(i, widths.size)
                ) { Text(stringResource(width.labelRes)) }
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

private val ColumnWidth.labelRes: Int
    get() = when (this) {
        ColumnWidth.NARROW -> R.string.view_width_narrow
        ColumnWidth.MEDIUM -> R.string.view_width_medium
        ColumnWidth.WIDE -> R.string.view_width_wide
    }

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )
}

/** A text button opening a menu of [options]; hidden when there are none. */
@Composable
fun <T> MenuButton(label: String, options: List<Pair<T, String>>, showAddIcon: Boolean = true, onPick: (T) -> Unit) {
    if (options.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            if (showAddIcon) Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(label)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        open = false
                        onPick(value)
                    }
                )
            }
        }
    }
}
