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
import androidx.compose.runtime.LaunchedEffect
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
import ca.stewartclan.andb.data.LinkProps
import ca.stewartclan.andb.data.LinkedProps
import ca.stewartclan.andb.data.ListProps
import ca.stewartclan.andb.ui.common.NameField
import ca.stewartclan.andb.ui.common.NameProblem
import ca.stewartclan.andb.ui.common.rememberNameState

/** What the List editor needs for choosing a lookup source. */
class LookupContext(
    /** The fields of the database being designed. */
    val currentFields: List<FieldDef>,
    val databases: List<DatabaseSummary>,
    val info: LookupInfo,
    val onChange: (String?, Long?) -> Unit,
    val currentDatabaseId: String
)

/** What [FieldDialog] needs to preview relinking a Link field pointed at another database. */
class RelinkContext(val preview: RelinkPreview?, val onLoad: (FieldDef, LinkProps) -> Unit)

/** Adds a field when [field] is null, otherwise edits its name, type and settings. */
@Composable
fun FieldDialog(
    title: String,
    confirmLabel: String,
    field: FieldDef?,
    recordCount: Long,
    lookup: LookupContext,
    relink: RelinkContext,
    /** Link settings to start with instead of the field's, e.g. a replacement for a deleted database. */
    startLink: LinkProps? = null,
    check: (String) -> NameProblem,
    /**
     * Gets the field as entered: name, type, settings and required (id and position unset), and
     * whether to relink by title if it's a Link pointed at another database.
     */
    onConfirm: (FieldDef, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val draft = rememberFieldDraft(field, startLink)
    // A database just chosen starts with the display field that matches the most saved titles.
    LaunchedEffect(relink.preview) {
        val preview = relink.preview ?: return@LaunchedEffect
        val link = draft.linkDraft
        if (preview.fieldId == field?.id && preview.link.databaseId == link.databaseId && link.displayFieldId == null) {
            preview.bestDisplayFieldId?.let { draft.linkDraft = link.copy(displayFieldId = it) }
        }
    }
    val problem = check(draft.name.text)
    val entered = draft.toField()
    val canConfirm = problem == NameProblem.OK && entered != null
    // Whether a Link pointed at another database keeps the links found there by title.
    var relinkByTitle by rememberSaveable { mutableStateOf(true) }
    val confirm = { if (canConfirm && entered != null) onConfirm(entered, relinkByTitle) }

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
                val new = entered ?: FieldDef(0, 0, "", type, FieldProps.default(type))
                if (field != null && isRepoint(field, new)) {
                    RelinkOptions(field, new, relink, relinkByTitle, onRelinkChange = { relinkByTitle = it })
                } else {
                    ChangeWarning(field, new, recordCount)
                }
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
    linkDraft: MutableState<LinkDraft>,
    linkedDraft: MutableState<LinkedProps>,
    required: MutableState<Boolean>
) {
    var name by name
    var type by type
    var idDraft by idDraft
    var listDraft by listDraft
    var linkDraft by linkDraft
    var linkedDraft by linkedDraft
    var required by required

    /** The field as entered (id and position unset), or null while a setting is invalid. */
    fun toField(): FieldDef? {
        val props = when (type) {
            FieldType.ID -> idDraft.toProps()
            FieldType.LIST -> listDraft.toProps()
            FieldType.LINK -> linkDraft.toProps()
            // A Linked field needs the link it shows through.
            FieldType.LINKED -> linkedDraft.takeIf { it.linkFieldId != 0L }
            else -> FieldProps.default(type)
        }
        return props?.let { FieldDef(0, 0, name.text, type, it, required && type.canBeRequired) }
    }
}

@Composable
private fun rememberFieldDraft(field: FieldDef?, startLink: LinkProps?): FieldDraft {
    val name = rememberNameState(field?.name.orEmpty())
    val type = rememberSaveable { mutableStateOf(field?.type ?: FieldType.STRING) }
    val idDraft = rememberSaveable(stateSaver = IdDraft.Saver) {
        mutableStateOf(IdDraft.of(field?.props as? IdProps ?: IdProps()))
    }
    val listDraft = rememberSaveable(stateSaver = ListDraft.Saver) {
        mutableStateOf((field?.props as? ListProps)?.let(ListDraft::of) ?: ListDraft())
    }
    val linkDraft = rememberSaveable(stateSaver = LinkDraft.Saver) {
        mutableStateOf((startLink ?: field?.props as? LinkProps)?.let(LinkDraft::of) ?: LinkDraft())
    }
    val linkedDraft = rememberSaveable(stateSaver = LinkedPropsSaver) {
        mutableStateOf(field?.props as? LinkedProps ?: LinkedProps())
    }
    val required = rememberSaveable { mutableStateOf(field?.required ?: false) }
    return remember { FieldDraft(name, type, idDraft, listDraft, linkDraft, linkedDraft, required) }
}

/** The settings of the draft's type, and the Required switch where it applies. */
@Composable
private fun FieldSettings(draft: FieldDraft, lookup: LookupContext, editingFieldId: Long?, modifier: Modifier) {
    when (draft.type) {
        FieldType.ID -> IdPropsEditor(draft.idDraft, { draft.idDraft = it }, modifier)
        FieldType.LIST -> ListPropsEditor(draft.listDraft, { draft.listDraft = it }, lookup, editingFieldId, modifier)
        FieldType.LINK -> LinkPropsEditor(draft.linkDraft, { draft.linkDraft = it }, lookup, modifier)
        FieldType.LINKED -> LinkedPropsEditor(draft.linkedDraft, { draft.linkedDraft = it }, lookup, modifier)
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
        ChangeEffect.RELINK -> return // RelinkOptions explains it.
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

/**
 * For Link field [old] pointed at [new]'s other database: a switch to [relink] by title, and what
 * that (or clearing the links instead) does, from [context]'s preview.
 */
@Composable
private fun RelinkOptions(
    old: FieldDef,
    new: FieldDef,
    context: RelinkContext,
    relink: Boolean,
    onRelinkChange: (Boolean) -> Unit
) {
    val link = new.recordLink ?: return
    LaunchedEffect(link) { context.onLoad(old, link) }
    val plan = context.preview?.takeIf { it.fieldId == old.id && it.link == link }?.plan
    val unmatched = plan?.let { it.notFound + it.ambiguous } ?: 0
    val total = plan?.let { it.links.size + unmatched } ?: 0
    // Nothing to carry over: no record has a link here.
    if (plan != null && total == 0) return
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.relink_by_title), modifier = Modifier.weight(1f))
            Switch(checked = relink, onCheckedChange = onRelinkChange)
        }
        val message = when {
            plan == null -> stringResource(R.string.relink_matching)
            !relink -> pluralStringResource(R.plurals.relink_clear, total, total)
            unmatched == 0 -> pluralStringResource(R.plurals.relink_found, plan.links.size, plan.links.size)
            else -> pluralStringResource(R.plurals.relink_found, plan.links.size, plan.links.size) + " " +
                pluralStringResource(R.plurals.relink_unmatched, unmatched, unmatched)
        }
        Text(
            message,
            color = if (relink) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
    }
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
