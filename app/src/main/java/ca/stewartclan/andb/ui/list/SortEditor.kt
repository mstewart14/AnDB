package ca.stewartclan.andb.ui.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.SortKey

/** The view editor's sort levels, most significant first, and a button to add one. */
fun LazyListScope.sortItems(state: ViewEditorState, viewModel: ViewEditorViewModel) {
    val sort = state.draft.sort
    if (sort.isEmpty()) {
        item {
            Text(
                stringResource(R.string.view_sort_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    itemsIndexed(sort, key = { i, key -> "sort-$i-${key.fieldId}" }) { index, key ->
        SortLevelRow(
            index = index,
            key = key,
            fields = state.sortFields.filter { f -> f.id == key.fieldId || sort.none { it.fieldId == f.id } },
            isLast = index == sort.lastIndex,
            onChange = { viewModel.setSortLevel(index, it) },
            onMove = { by -> viewModel.edit { it.copy(sort = it.sort.moved(index, index + by)) } },
            onRemove = { viewModel.removeSortLevel(index) }
        )
    }
    if (state.sortFields.any { f -> sort.none { it.fieldId == f.id } }) {
        item {
            TextButton(onClick = viewModel::addSortLevel) { Text(stringResource(R.string.view_add_sort)) }
        }
    }
}

@Composable
private fun SortLevelRow(
    index: Int,
    key: SortKey,
    fields: List<FieldDef>,
    isLast: Boolean,
    onChange: (SortKey) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit
) {
    val field = fields.find { it.id == key.fieldId }
    val isText = field?.isTextual == true
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1}.", modifier = Modifier.padding(end = 8.dp))
            Box(Modifier.weight(1f)) {
                MenuButton(field?.name.orEmpty(), fields.map { it to it.name }, showAddIcon = false) {
                    onChange(key.copy(fieldId = it.id))
                }
            }
            SortLevelMenu(canMoveUp = index > 0, canMoveDown = !isLast, onMove = onMove, onRemove = onRemove)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = key.descending,
                onClick = { onChange(key.copy(descending = !key.descending)) },
                label = {
                    Text(
                        stringResource(
                            if (key.descending) R.string.view_sort_descending else R.string.view_sort_ascending
                        )
                    )
                }
            )
            if (isText) {
                FilterChip(
                    selected = key.caseSensitive,
                    onClick = { onChange(key.copy(caseSensitive = !key.caseSensitive)) },
                    label = { Text(stringResource(R.string.view_sort_case_sensitive)) },
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun SortLevelMenu(canMoveUp: Boolean, canMoveDown: Boolean, onMove: (Int) -> Unit, onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_move_up)) },
                enabled = canMoveUp,
                onClick = {
                    open = false
                    onMove(-1)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_move_down)) },
                enabled = canMoveDown,
                onClick = {
                    open = false
                    onMove(1)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_remove)) },
                onClick = {
                    open = false
                    onRemove()
                }
            )
        }
    }
}
