package ca.stewartclan.andb.ui.record

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.stewartclan.andb.R
import ca.stewartclan.andb.ui.common.ErrorSnackbar
import kotlinx.serialization.Serializable

/** Navigation route for one record of a database; [NEW_RECORD_ID] starts a new one. */
@Serializable
data class RecordRoute(val databaseId: String, val recordId: Long)

/** Record id of a record being entered and not yet stored. Stored records' ids start at 1. */
const val NEW_RECORD_ID = 0L

@Composable
fun RecordScreen(
    onBack: () -> Unit,
    onOpenRecord: (databaseId: String, recordId: Long) -> Unit,
    viewModel: RecordViewModel = viewModel(factory = RecordViewModel.Factory)
) {
    val database by viewModel.database.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    ErrorSnackbar(error, snackbarHostState, viewModel::errorShown)
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // Where the user asked to go while required fields were empty: BACK, or -1/+1 for previous/next.
    var pendingLeave by rememberSaveable { mutableStateOf<Int?>(null) }

    val goNow = { by: Int ->
        if (by == BACK) {
            viewModel.saveInBackground()
            onBack()
        } else {
            viewModel.go(by)
        }
    }
    // Leaving when edits can't be saved (required fields empty) asks first.
    val go = { by: Int -> if (state?.wouldLoseEdits == true) pendingLeave = by else goNow(by) }
    BackHandler { go(BACK) }
    // Switching apps or turning the screen off saves too.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.saveInBackground() }
    // Linked records may have changed while another screen was shown.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshLinks() }
    LaunchedEffect(finished) { if (finished) onBack() }

    RecordContent(
        title = database?.name.orEmpty(),
        state = state,
        locked = locked,
        snackbarHostState = snackbarHostState,
        actions = RecordActions(
            onBack = { go(BACK) },
            onLockedChange = viewModel::setLocked,
            onGo = go,
            onNew = viewModel::newRecord,
            onDuplicate = { viewModel.duplicate() },
            onDelete = { confirmDelete = true }
        ),
        editor = {
            viewModel.editorContext(it, locked) { ref ->
                viewModel.saveInBackground()
                onOpenRecord(ref.databaseId, ref.recordId)
            }
        }
    )

    if (confirmDelete) DeleteRecordDialog(onConfirm = { viewModel.delete() }, onClose = { confirmDelete = false })
    pendingLeave?.let { leaving ->
        state?.let {
            RequiredLeaveDialog(
                it,
                onDiscard = {
                    pendingLeave = null
                    viewModel.revert()
                    goNow(leaving)
                },
                onDone = { pendingLeave = null }
            )
        }
    }
}

/** What the record screen's buttons do. */
private class RecordActions(
    val onBack: () -> Unit,
    val onLockedChange: (Boolean) -> Unit,
    val onGo: (Int) -> Unit,
    val onNew: () -> Unit,
    val onDuplicate: () -> Unit,
    val onDelete: () -> Unit
)

/** [onConfirm] deletes; [onClose] closes the dialog, after either button. */
@Composable
private fun DeleteRecordDialog(onConfirm: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.record_delete_title)) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onClose()
            }) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/** Asks before leaving a record whose edits can't be saved because required fields are empty. */
@Composable
private fun RequiredLeaveDialog(state: RecordUiState, onDiscard: () -> Unit, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.record_required_title)) },
        text = {
            Text(stringResource(R.string.record_required_body, state.missing.joinToString(", ") { it.name }))
        },
        confirmButton = {
            TextButton(onClick = onDiscard) {
                Text(stringResource(R.string.record_discard), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.record_keep_editing)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordContent(
    title: String,
    state: RecordUiState?,
    locked: Boolean,
    snackbarHostState: SnackbarHostState,
    actions: RecordActions,
    editor: (RecordUiState) -> EditorContext
) {
    Scaffold(
        topBar = { RecordTopBar(title, locked, actions) },
        bottomBar = { if (state != null) PrevNextBar(state, actions.onGo) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (state != null) RecordForm(state, editor(state), actions.onGo, Modifier.padding(innerPadding))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordTopBar(title: String, locked: Boolean, actions: RecordActions) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = actions.onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        },
        actions = {
            IconButton(onClick = actions.onNew) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.list_new_record))
            }
            IconToggleButton(checked = locked, onCheckedChange = actions.onLockedChange) {
                Icon(
                    if (locked) Icons.Filled.Lock else Icons.Outlined.Lock,
                    contentDescription = stringResource(R.string.record_protect)
                )
            }
            RecordMenu(actions.onDuplicate, actions.onDelete)
        }
    )
}

/** All the record's fields, scrolling; swiping sideways calls [onGo] with +1 (next) or -1. */
@Composable
private fun RecordForm(state: RecordUiState, ctx: EditorContext, onGo: (Int) -> Unit, modifier: Modifier) {
    val swipeDistance = with(LocalDensity.current) { SWIPE_DISTANCE.toPx() }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .pointerInput(state.record.id) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onHorizontalDrag = { _, dx -> total += dx },
                    onDragEnd = {
                        when {
                            total <= -swipeDistance -> onGo(1)
                            total >= swipeDistance -> onGo(-1)
                        }
                    }
                )
            }
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (state.showMissing && state.missing.isNotEmpty()) {
            Text(
                stringResource(R.string.record_fill_required),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (state.fields.isEmpty()) {
            Text(stringResource(R.string.record_no_fields), style = MaterialTheme.typography.bodyMedium)
        }
        state.fields.forEach { field -> FieldEditor(field, ctx) }
    }
}

@Composable
private fun RecordMenu(onDuplicate: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.record_duplicate)) },
                onClick = {
                    open = false
                    onDuplicate()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                onClick = {
                    open = false
                    onDelete()
                }
            )
        }
    }
}

@Composable
private fun PrevNextBar(state: RecordUiState, onGo: (Int) -> Unit) {
    BottomAppBar {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = { onGo(-1) }, enabled = state.index > 0) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.record_previous)
                )
            }
            Text(
                if (state.isNew) {
                    stringResource(R.string.record_new)
                } else {
                    stringResource(R.string.record_position, state.index + 1, state.recordIds.size)
                }
            )
            IconButton(onClick = { onGo(1) }, enabled = state.index < state.recordIds.lastIndex) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.record_next)
                )
            }
        }
    }
}

private val SWIPE_DISTANCE = 96.dp

/** [RecordScreen]'s pending-leave value for going back, as opposed to -1/+1 for previous/next. */
private const val BACK = 0
