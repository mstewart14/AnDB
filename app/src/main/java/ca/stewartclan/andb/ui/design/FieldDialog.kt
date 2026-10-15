package ca.stewartclan.andb.ui.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.DatabaseSummary
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.FieldProps
import ca.stewartclan.andb.data.FieldType
import ca.stewartclan.andb.data.IdProps
import ca.stewartclan.andb.data.ListProps
import ca.stewartclan.andb.ui.common.NameField
import ca.stewartclan.andb.ui.common.NameProblem
import ca.stewartclan.andb.ui.common.rememberNameState

/** What the List editor needs for choosing a lookup source. */
class LookupContext(
    val databases: List<DatabaseSummary>,
    val info: LookupInfo,
    val onChange: (String?, Long?) -> Unit,
    val currentDatabaseId: String
)

/** Adds a field when [field] is null, otherwise edits its name, type and settings. */
@Composable
fun FieldDialog(
    title: String,
    confirmLabel: String,
    field: FieldDef?,
    recordCount: Long,
    lookup: LookupContext,
    check: (String) -> NameProblem,
    /** Gets the field as entered: name, type, settings and required (id and position unset). */
    onConfirm: (FieldDef) -> Unit,
    onDismiss: () -> Unit
) {
    val draft = rememberFieldDraft(field)
    val problem = check(draft.name.text)
    val entered = draft.toField()
    val canConfirm = problem == NameProblem.OK && entered != null
    val confirm = { if (canConfirm && entered != null) onConfirm(entered) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                NameField(
                    value = draft.name,
                    onValueChange = { draft.name = it },
                    problem = problem,
                    duplicateMessage = stringResource(R.string.design_name_duplicate),
                    onDone = confirm,
                    modifier = Modifier.fillMaxWidth()
                )
                TypePicker(draft.type, onTypeChange = { draft.type = it }, modifier = Modifier.padding(top = 8.dp))
                FieldSettings(draft, lookup, field?.id, Modifier.padding(top = 8.dp))
                val type = draft.type
                ChangeWarning(field, entered ?: FieldDef(0, 0, "", type, FieldProps.default(type)), recordCount)
            }
        },
        confirmButton = {
            TextButton(onClick = confirm, enabled = canConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/** The field being entered in [FieldDialog]; each part survives rotation. */
class FieldDraft(
    name: MutableState<TextFieldValue>,
    type: MutableState<FieldType>,
    idDraft: MutableState<IdDraft>,
    listDraft: MutableState<ListDraft>,
    required: MutableState<Boolean>
) {
    var name by name
    var type by type
    var idDraft by idDraft
    var listDraft by listDraft
    var required by required

    /** The field as entered (id and position unset), or null while a setting is invalid. */
    fun toField(): FieldDef? {
        val props = when (type) {
            FieldType.ID -> idDraft.toProps()
            FieldType.LIST -> listDraft.toProps()
            else -> FieldProps.default(type)
        }
        return props?.let { FieldDef(0, 0, name.text, type, it, required && type.canBeRequired) }
    }
}

@Composable
private fun rememberFieldDraft(field: FieldDef?): FieldDraft {
    val name = rememberNameState(field?.name.orEmpty())
    val type = rememberSaveable { mutableStateOf(field?.type ?: FieldType.STRING) }
    val idDraft = rememberSaveable(stateSaver = IdDraft.Saver) {
        mutableStateOf(IdDraft.of(field?.props as? IdProps ?: IdProps()))
    }
    val listDraft = rememberSaveable(stateSaver = ListDraft.Saver) {
        mutableStateOf((field?.props as? ListProps)?.let(ListDraft::of) ?: ListDraft())
    }
    val required = rememberSaveable { mutableStateOf(field?.required ?: false) }
    return remember { FieldDraft(name, type, idDraft, listDraft, required) }
}

/** The settings of the draft's type, and the Required switch where it applies. */
@Composable
private fun FieldSettings(draft: FieldDraft, lookup: LookupContext, editingFieldId: Long?, modifier: Modifier) {
    when (draft.type) {
        FieldType.ID -> IdPropsEditor(draft.idDraft, { draft.idDraft = it }, modifier)
        FieldType.LIST -> ListPropsEditor(draft.listDraft, { draft.listDraft = it }, lookup, editingFieldId, modifier)
        FieldType.LINK -> Text(
            stringResource(R.string.link_photo_explained),
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier
        )
        else -> Unit
    }
    if (draft.type.canBeRequired) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
            Text(stringResource(R.string.design_required), modifier = Modifier.weight(1f))
            Switch(checked = draft.required, onCheckedChange = { draft.required = it })
        }
    }
}

/** Explains what saving [new] over [old] does to the values already in [recordCount] records. */
@Composable
private fun ChangeWarning(old: FieldDef?, new: FieldDef, recordCount: Long) {
    val effect = changeEffect(old, new)
    if (recordCount == 0L || effect == null) return
    val count = recordCount.toInt()
    val message = when (effect) {
        ChangeEffect.ID_BACKFILL -> pluralStringResource(R.plurals.id_backfill_info, count, recordCount)
        ChangeEffect.ID_REPLACE -> pluralStringResource(R.plurals.id_replace_warning, count, recordCount)
        ChangeEffect.ID_REGENERATE -> pluralStringResource(R.plurals.id_regenerate_warning, count, recordCount)
        ChangeEffect.KEEP_FIRST_CHOICE -> stringResource(R.string.list_single_warning)
        ChangeEffect.CONVERT -> pluralStringResource(R.plurals.design_type_change_warning, count, recordCount)
    }
    Text(
        message,
        color = if (effect == ChangeEffect.ID_BACKFILL) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.error
        },
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 8.dp)
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
fun DeleteFieldDialog(field: FieldDef, recordCount: Long, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.design_delete_title, field.name)) },
        text = if (field.hasValues && recordCount > 0) {
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
