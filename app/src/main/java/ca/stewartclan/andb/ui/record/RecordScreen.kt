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

/** Navigation route for one record of a database. */
@Serializable
data class RecordRoute(val databaseId: String, val recordId: Long)

@Composable
fun RecordScreen(onBack: () -> Unit, viewModel: RecordViewModel = viewModel(factory = RecordViewModel.Factory)) {
    val database by viewModel.database.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    ErrorSnackbar(error, snackbarHostState, viewModel::errorShown)
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val leave = {
        viewModel.saveInBackground()
        onBack()
    }
    BackHandler(onBack = leave)
    // Switching apps or turning the screen off saves too.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.saveInBackground() }
    LaunchedEffect(finished) { if (finished) onBack() }

    RecordContent(
        title = database?.name.orEmpty(),
        state = state,
        locked = locked,
        snackbarHostState = snackbarHostState,
        onBack = leave,
        onLockedChange = viewModel::setLocked,
        onGo = viewModel::go,
        onDuplicate = { viewModel.duplicate() },
        onDelete = { confirmDelete = true },
        editor = { state ->
            EditorContext(state, readOnly = locked, onEdit = viewModel::edit, onToggleChoice = viewModel::toggleChoice)
        }
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.record_delete_title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete()
                    confirmDelete = false
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordContent(
    title: String,
    state: RecordUiState?,
    locked: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onLockedChange: (Boolean) -> Unit,
    onGo: (Int) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    editor: (RecordUiState) -> EditorContext
) {
    Scaffold(
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
                actions = {
                    IconToggleButton(checked = locked, onCheckedChange = onLockedChange) {
                        Icon(
                            if (locked) Icons.Filled.Lock else Icons.Outlined.Lock,
                            contentDescription = stringResource(R.string.record_protect)
                        )
                    }
                    RecordMenu(onDuplicate, onDelete)
                }
            )
        },
        bottomBar = { if (state != null) PrevNextBar(state, onGo) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (state == null) return@Scaffold
        val swipeDistance = with(LocalDensity.current) { SWIPE_DISTANCE.toPx() }
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
            if (state.fields.isEmpty()) {
                Text(stringResource(R.string.record_no_fields), style = MaterialTheme.typography.bodyMedium)
            }
            val ctx = editor(state)
            state.fields.forEach { field -> FieldEditor(field, ctx) }
        }
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
            Text(stringResource(R.string.record_position, state.index + 1, state.recordIds.size))
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
