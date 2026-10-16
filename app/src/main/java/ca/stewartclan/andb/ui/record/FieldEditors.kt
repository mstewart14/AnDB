package ca.stewartclan.andb.ui.record

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.FieldType
import ca.stewartclan.andb.data.LinkValue
import ca.stewartclan.andb.data.LinkedProps
import ca.stewartclan.andb.data.RecordRef

/** The record being edited, whether it may be changed, and how to report changes. */
class EditorContext(
    /** The database the record is in, where its copied photos are. */
    val databaseId: String,
    val state: RecordUiState,
    val readOnly: Boolean,
    val onEdit: (FieldDef, String) -> Unit,
    val onToggleChoice: (FieldDef, String) -> Unit,
    val onLink: (FieldDef, LinkValue?) -> Unit,
    /** Opens a linked record. */
    val onOpenRecord: (RecordRef) -> Unit
)

/**
 * The input for one field of the record. Required fields are marked in their label; empty ones
 * are pointed out once the user has tried to move on without them.
 */
@Composable
fun FieldEditor(field: FieldDef, ctx: EditorContext, modifier: Modifier = Modifier) {
    val labelled = if (field.required) field.copy(name = "${field.name} *") else field
    val missing = ctx.state.showMissing && ctx.state.missing.any { it.id == field.id }
    Column(modifier) {
        TypedEditor(labelled, ctx, Modifier)
        if (missing) {
            Text(
                stringResource(R.string.record_required),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

/** The input for [field], chosen by its type. */
@Composable
private fun TypedEditor(field: FieldDef, ctx: EditorContext, modifier: Modifier) {
    val text = ctx.state.drafts[field.id].orEmpty()
    when (field.type) {
        FieldType.SECTION, FieldType.ID, FieldType.CREATED, FieldType.MODIFIED, FieldType.LINKED ->
            ReadOnlyEditor(field, ctx, modifier)
        FieldType.BOOLEAN, FieldType.RATING, FieldType.SCALE, FieldType.DATE, FieldType.TIME ->
            ValueEditor(field, text, ctx, modifier)
        FieldType.LIST -> if (field.isMultiList) {
            ChoiceChips(field, ctx, modifier)
        } else {
            val choices = ctx.state.listChoices[field.id].orEmpty()
            ListDropdown(field.name, text, choices, ctx.readOnly, { ctx.onEdit(field, it) }, modifier)
        }
        FieldType.LINK -> when {
            field.recordLink != null -> RecordLinkEditor(field, ctx, modifier)
            field.isPhotoLink -> PhotoEditor(field, ctx, modifier)
            else -> DeviceLinkEditor(field, ctx, modifier)
        }
        else -> TextEditor(field, text, ctx.readOnly, { ctx.onEdit(field, it) }, modifier)
    }
}

/** Fields shown but never edited: section headers, IDs and timestamps. */
@Composable
private fun ReadOnlyEditor(field: FieldDef, ctx: EditorContext, modifier: Modifier) {
    val record = ctx.state.record
    when (field.type) {
        FieldType.SECTION -> SectionHeader(field.name, modifier)
        FieldType.CREATED -> ReadOnlyValue(field.name, FieldValues.timestamp(record.created), modifier)
        FieldType.MODIFIED -> ReadOnlyValue(field.name, FieldValues.timestamp(record.modified), modifier)
        FieldType.LINKED -> ReadOnlyValue(field.name, linkedText(field, ctx.state), modifier)
        else -> ReadOnlyValue(field.name, FieldValues.display(field, record.values[field.id]), modifier)
    }
}

/** Fields with a dedicated input rather than a text box. */
@Composable
private fun ValueEditor(field: FieldDef, text: String, ctx: EditorContext, modifier: Modifier) {
    val onText = { value: String -> ctx.onEdit(field, value) }
    val name = field.name
    val readOnly = ctx.readOnly
    when (field.type) {
        FieldType.BOOLEAN -> CheckboxEditor(name, text == "1", readOnly, { onText(if (it) "1" else "0") }, modifier)
        FieldType.RATING -> RatingEditor(name, text.toIntOrNull() ?: 0, readOnly, { onText(it.toString()) }, modifier)
        FieldType.SCALE -> ScaleEditor(name, text.toIntOrNull(), readOnly, {
            onText(it?.toString().orEmpty())
        }, modifier)
        FieldType.DATE -> DateEditor(name, text, readOnly, onText, modifier)
        else -> TimeEditor(name, text, readOnly, onText, modifier)
    }
}

@Composable
private fun TextEditor(field: FieldDef, text: String, readOnly: Boolean, onText: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val keyboard = keyboardFor(field.type)
    val action = text.takeIf { it.isNotBlank() }?.let { actionFor(field.type, it.trim()) }
    OutlinedTextField(
        value = text,
        onValueChange = { onText(filterFor(field.type, it)) },
        label = { Text(field.name) },
        readOnly = readOnly,
        singleLine = field.type != FieldType.NOTE,
        minLines = if (field.type == FieldType.NOTE) NOTE_LINES else 1,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard,
            capitalization = if (keyboard == KeyboardType.Text) {
                KeyboardCapitalization.Sentences
            } else {
                KeyboardCapitalization.None
            }
        ),
        trailingIcon = action?.let { (icon, intent) ->
            {
                IconButton(onClick = { context.startSafely(intent) }) {
                    Icon(icon, contentDescription = stringResource(R.string.record_open_value))
                }
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}

private fun keyboardFor(type: FieldType): KeyboardType = when (type) {
    FieldType.INTEGER -> KeyboardType.Number
    FieldType.FLOAT -> KeyboardType.Decimal
    FieldType.EMAIL -> KeyboardType.Email
    FieldType.PHONE -> KeyboardType.Phone
    FieldType.URL -> KeyboardType.Uri
    else -> KeyboardType.Text
}

private fun filterFor(type: FieldType, text: String): String = when (type) {
    FieldType.INTEGER -> FieldValues.filterNumber(text, decimal = false)
    FieldType.FLOAT -> FieldValues.filterNumber(text, decimal = true)
    else -> text
}

/** The button that acts on a value (send mail, dial, open in browser), if the type has one. */
private fun actionFor(type: FieldType, value: String): Pair<ImageVector, Intent>? = when (type) {
    FieldType.EMAIL -> Icons.Filled.Email to Intent(Intent.ACTION_SENDTO, "mailto:$value".toUri())
    FieldType.PHONE -> Icons.Filled.Call to Intent(Intent.ACTION_DIAL, "tel:$value".toUri())
    FieldType.URL ->
        Icons.AutoMirrored.Filled.Send to
            Intent(Intent.ACTION_VIEW, (if ("://" in value) value else "https://$value").toUri())
    else -> null
}

private fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No app handles it (e.g. no dialer on a tablet); nothing useful to do.
    }
}

private const val NOTE_LINES = 3

/** What Linked [field] shows: its target field of the record linked through its Link field. */
private fun linkedText(field: FieldDef, state: RecordUiState): String {
    val linkFieldId = (field.props as? LinkedProps)?.linkFieldId
    val linkField = state.fields.find { it.id == linkFieldId }
    val recordId = linkField?.linkedRecordId(state.links[linkField.id])
    return recordId?.let { state.linkedValues[field.id]?.get(it) }.orEmpty()
}
