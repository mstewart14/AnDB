package ca.stewartclan.andb.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.Filter

/** Choosing, applying, adding and editing saved filters. */
class FilterActions(
    /** Opens the filter chooser. */
    val onChoose: () -> Unit,
    /** Applies a filter, or none. */
    val onSelect: (Filter?) -> Unit,
    val onNew: () -> Unit,
    val onEdit: (Filter) -> Unit
)

/** Lists the saved filters to apply one (or none), with buttons to edit each and add one. */
@Composable
fun FilterDialog(filters: List<Filter>, current: Filter?, actions: FilterActions, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.filter_title)) },
        text = {
            Column {
                FilterOption(stringResource(R.string.filter_none), current == null, onPick = {
                    actions.onSelect(null)
                    onClose()
                })
                filters.forEach { filter ->
                    FilterOption(
                        filter.name,
                        selected = filter.id == current?.id,
                        onPick = {
                            actions.onSelect(filter)
                            onClose()
                        },
                        onEdit = {
                            actions.onEdit(filter)
                            onClose()
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                actions.onNew()
                onClose()
            }) { Text(stringResource(R.string.filter_new)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) } }
    )
}

@Composable
private fun FilterOption(name: String, selected: Boolean, onPick: () -> Unit, onEdit: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPick)
    ) {
        RadioButton(selected = selected, onClick = onPick)
        Text(name, modifier = Modifier.weight(1f))
        if (onEdit != null) {
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.filter_edit))
            }
        }
    }
}

/** Shows the filter being applied, with the count it leaves; tap to change, ✕ to remove. */
@Composable
fun ActiveFilterRow(filter: Filter, matchCount: Int, totalCount: Int, onChange: () -> Unit, onClear: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 12.dp)
    ) {
        FilterChip(
            selected = true,
            onClick = onChange,
            label = { Text(stringResource(R.string.filter_active, filter.name)) },
            trailingIcon = {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.filter_clear),
                    modifier = Modifier.clickable(onClick = onClear)
                )
            }
        )
        Text(
            stringResource(R.string.find_count, matchCount, totalCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
