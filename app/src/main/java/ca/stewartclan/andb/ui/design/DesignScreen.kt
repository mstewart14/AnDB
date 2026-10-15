package ca.stewartclan.andb.ui.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.FieldType
import ca.stewartclan.andb.data.Storage
import ca.stewartclan.andb.ui.common.ErrorSnackbar
import ca.stewartclan.andb.ui.common.NameField
import ca.stewartclan.andb.ui.common.NameProblem
import ca.stewartclan.andb.ui.common.rememberNameState
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
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    ErrorSnackbar(error, snackbarHostState, viewModel::errorShown)
    // Dialogs remember the field id (or NEW_FIELD_ID), so they survive rotation.
    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val recordCount = database?.recordCount ?: 0

    DesignContent(
        databaseName = database?.name.orEmpty(),
        fields = fields,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onAdd = { editId = NEW_FIELD_ID },
        onEdit = { editId = it.id },
        onMove = viewModel::moveField,
        onDelete = { deleteId = it.id }
    )

    if (editId == NEW_FIELD_ID) {
        FieldDialog(
            title = stringResource(R.string.design_add_field),
            confirmLabel = stringResource(R.string.action_add),
            field = null,
            recordCount = recordCount,
            check = viewModel::checkName,
            onConfirm = { name, type ->
                viewModel.addField(name, type)
                editId = null
            },
            onDismiss = { editId = null }
        )
    }
    fields?.find { it.id == editId }?.let { field ->
        FieldDialog(
            title = stringResource(R.string.design_edit_field),
            confirmLabel = stringResource(R.string.action_save),
            field = field,
            recordCount = recordCount,
            check = { viewModel.checkName(it, editing = field) },
            onConfirm = { name, type ->
                viewModel.updateField(field, name, type)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignContent(
    databaseName: String,
    fields: List<FieldDef>?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (FieldDef) -> Unit,
    onMove: (FieldDef, Int) -> Unit,
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
            else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = innerPadding) {
                itemsIndexed(fields, key = { _, field -> field.id }) { index, field ->
                    FieldRow(
                        field = field,
                        canMoveUp = index > 0,
                        canMoveDown = index < fields.lastIndex,
                        onEdit = { onEdit(field) },
                        onMove = { by -> onMove(field, by) },
                        onDelete = { onDelete(field) }
                    )
                    HorizontalDivider()
                }
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
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onEdit),
        headlineContent = { Text(field.name) },
        supportingContent = { Text(stringResource(field.type.labelRes)) },
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

/** Adds a field when [field] is null, otherwise edits its name and type. */
@Composable
private fun FieldDialog(
    title: String,
    confirmLabel: String,
    field: FieldDef?,
    recordCount: Long,
    check: (String) -> NameProblem,
    onConfirm: (String, FieldType) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberNameState(field?.name.orEmpty())
    var type by rememberSaveable { mutableStateOf(field?.type ?: FieldType.STRING) }
    val problem = check(name.text)
    val losesValues = field != null && recordCount > 0 && !field.type.keepsValuesAs(type)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                NameField(
                    value = name,
                    onValueChange = { name = it },
                    problem = problem,
                    duplicateMessage = stringResource(R.string.design_name_duplicate),
                    onDone = { onConfirm(name.text, type) },
                    modifier = Modifier.fillMaxWidth()
                )
                TypePicker(type, onTypeChange = { type = it }, modifier = Modifier.padding(top = 8.dp))
                if (losesValues) {
                    Text(
                        pluralStringResource(R.plurals.design_type_change_warning, recordCount.toInt(), recordCount),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.text, type) }, enabled = problem == NameProblem.OK) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypePicker(type: FieldType, onTypeChange: (FieldType) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = stringResource(type.labelRes),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.design_type_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            designableTypes.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    onClick = {
                        onTypeChange(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@Composable
private fun DeleteFieldDialog(field: FieldDef, recordCount: Long, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.design_delete_title, field.name)) },
        text = if (field.type.storage != Storage.NONE && recordCount > 0) {
            {
                Text(pluralStringResource(R.plurals.design_delete_body, recordCount.toInt(), recordCount))
            }
        } else {
            null
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
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
                FieldDef(4, 3, "Rating", FieldType.RATING)
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onAdd = {},
            onEdit = {},
            onMove = { _, _ -> },
            onDelete = {}
        )
    }
}
