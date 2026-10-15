package ca.stewartclan.andb.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.FieldDef

/** What the editor shows about the chosen lookup source; loaded by the ViewModel. */
data class LookupInfo(val fields: List<FieldDef>? = null, val values: List<String>? = null)

/** How many lookup values to show as a sample. */
private const val SAMPLE_SIZE = 5

@Composable
fun ListPropsEditor(
    draft: ListDraft,
    onDraftChange: (ListDraft) -> Unit,
    lookup: LookupContext,
    editingFieldId: Long?,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !draft.useLookup,
                onClick = { onDraftChange(draft.copy(useLookup = false)) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text(stringResource(R.string.list_source_fixed)) }
            SegmentedButton(
                selected = draft.useLookup,
                onClick = { onDraftChange(draft.copy(useLookup = true)) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text(stringResource(R.string.list_source_lookup)) }
        }
        if (draft.useLookup) {
            LaunchedEffect(draft.lookupDatabaseId, draft.lookupFieldId) {
                lookup.onChange(draft.lookupDatabaseId, draft.lookupFieldId)
            }
            LookupEditor(draft, onDraftChange, lookup, editingFieldId)
        } else {
            ItemsEditor(draft, onDraftChange)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(stringResource(R.string.list_multiple), modifier = Modifier.weight(1f))
            Switch(checked = draft.multiple, onCheckedChange = { onDraftChange(draft.copy(multiple = it)) })
        }
    }
}

@Composable
private fun ItemsEditor(draft: ListDraft, onDraftChange: (ListDraft) -> Unit) {
    val bad = draft.badItems
    draft.items.forEachIndexed { index, item ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            OutlinedTextField(
                value = item,
                onValueChange = { onDraftChange(draft.withItem(index, it)) },
                placeholder = { Text(stringResource(R.string.list_item_placeholder, index + 1)) },
                singleLine = true,
                isError = index in bad,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f)
            )
            ItemMenu(
                canMoveUp = index > 0,
                canMoveDown = index < draft.items.lastIndex,
                canRemove = draft.items.size > 1,
                onMoveUp = { onDraftChange(draft.withItemMoved(index, index - 1)) },
                onMoveDown = { onDraftChange(draft.withItemMoved(index, index + 1)) },
                onRemove = { onDraftChange(draft.withoutItem(index)) }
            )
        }
    }
    if (bad.isNotEmpty()) {
        Text(
            stringResource(R.string.list_items_invalid),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    TextButton(onClick = { onDraftChange(draft.copy(items = draft.items + "")) }) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text(stringResource(R.string.list_add_item), modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ItemMenu(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canRemove: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                Triple(R.string.action_move_up, canMoveUp, onMoveUp),
                Triple(R.string.action_move_down, canMoveDown, onMoveDown),
                Triple(R.string.action_remove, canRemove, onRemove)
            ).forEach { (label, enabled, action) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    enabled = enabled,
                    onClick = {
                        open = false
                        action()
                    }
                )
            }
        }
    }
}

@Composable
private fun LookupEditor(
    draft: ListDraft,
    onDraftChange: (ListDraft) -> Unit,
    lookup: LookupContext,
    editingFieldId: Long?
) {
    val databases = lookup.databases
    Picker(
        label = stringResource(R.string.list_lookup_database),
        selected = databases.find { it.id == draft.lookupDatabaseId }?.name,
        options = databases.map { it.id to it.name },
        onSelect = { onDraftChange(draft.copy(lookupDatabaseId = it, lookupFieldId = null)) },
        modifier = Modifier.padding(top = 8.dp)
    )
    if (draft.lookupDatabaseId == null) return
    // A field can't look itself up, and fields without values have nothing to offer.
    val fields = lookup.info.fields.orEmpty().filter {
        it.hasValues && !(draft.lookupDatabaseId == lookup.currentDatabaseId && it.id == editingFieldId)
    }
    Picker(
        label = stringResource(R.string.list_lookup_field),
        selected = fields.find { it.id == draft.lookupFieldId }?.name,
        options = fields.map { it.id to it.name },
        onSelect = { onDraftChange(draft.copy(lookupFieldId = it)) },
        modifier = Modifier.padding(top = 8.dp)
    )
    val values = lookup.info.values
    if (draft.lookupFieldId != null && values != null) {
        val sample = values.take(SAMPLE_SIZE).joinToString(", ") + if (values.size > SAMPLE_SIZE) ", …" else ""
        Text(
            if (values.isEmpty()) {
                stringResource(R.string.list_lookup_no_values)
            } else {
                pluralStringResource(R.plurals.list_lookup_values, values.size, values.size, sample)
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <K> Picker(
    label: String,
    selected: String?,
    options: List<Pair<K, String>>,
    onSelect: (K) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected.orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(key)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
