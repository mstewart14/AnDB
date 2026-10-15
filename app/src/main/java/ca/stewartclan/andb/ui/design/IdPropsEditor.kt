package ca.stewartclan.andb.ui.design

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.IdKind
import ca.stewartclan.andb.data.IdProps
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** ID settings as typed, before validation; numbers are kept as text so any input can be shown. */
@Serializable
data class IdDraft(val kind: IdKind, val start: String, val prefix: String, val padding: String) {
    val startValid: Boolean get() = start.toLongOrNull()?.let { it >= 0 } == true
    val prefixValid: Boolean get() = prefix.length <= IdProps.MAX_PREFIX_LENGTH
    val paddingValid: Boolean get() = padding.toIntOrNull()?.let { it in 0..IdProps.MAX_PADDING } == true

    /** The settings, or null if an increment setting is invalid. Other kinds ignore them. */
    fun toProps(): IdProps? = when {
        kind != IdKind.INCREMENT -> IdProps(kind)
        startValid && prefixValid && paddingValid -> IdProps(kind, start.toLong(), prefix, padding.toInt())
        else -> null
    }

    companion object {
        fun of(props: IdProps) = IdDraft(props.kind, props.start.toString(), props.prefix, props.padding.toString())

        val Saver = Saver<IdDraft, String>(
            save = { Json.encodeToString(serializer(), it) },
            restore = { Json.decodeFromString(serializer(), it) }
        )
    }
}

@Composable
fun IdPropsEditor(draft: IdDraft, onDraftChange: (IdDraft) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        IdKindPicker(draft.kind, onKindChange = { onDraftChange(draft.copy(kind = it)) })
        if (draft.kind == IdKind.INCREMENT) {
            OutlinedTextField(
                value = draft.start,
                onValueChange = { onDraftChange(draft.copy(start = it)) },
                label = { Text(stringResource(R.string.id_start_label)) },
                singleLine = true,
                isError = !draft.startValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
            OutlinedTextField(
                value = draft.prefix,
                onValueChange = { onDraftChange(draft.copy(prefix = it)) },
                label = { Text(stringResource(R.string.id_prefix_label)) },
                singleLine = true,
                isError = !draft.prefixValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
            OutlinedTextField(
                value = draft.padding,
                onValueChange = { onDraftChange(draft.copy(padding = it)) },
                label = { Text(stringResource(R.string.id_padding_label, IdProps.MAX_PADDING)) },
                singleLine = true,
                isError = !draft.paddingValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }
        val example = when (draft.kind) {
            IdKind.INCREMENT -> draft.toProps()?.let { it.format(it.start) }
            IdKind.UUID_RANDOM -> "3f2b9c1e-8d4a-4f6b-9e21-7c5d0a8b4e13"
            IdKind.UUID_TIME -> "0192f3a4-7b1c-7e2d-9f40-6a8b3c1d5e27"
            IdKind.SHORT_CODE -> "K7QX-29MD"
        }
        if (example != null) {
            Text(
                stringResource(R.string.id_example, example),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdKindPicker(kind: IdKind, onKindChange: (IdKind) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(kind.labelRes),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.id_kind_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            IdKind.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    onClick = {
                        onKindChange(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@get:StringRes
val IdKind.labelRes: Int
    get() = when (this) {
        IdKind.INCREMENT -> R.string.id_kind_increment
        IdKind.UUID_RANDOM -> R.string.id_kind_uuid_random
        IdKind.UUID_TIME -> R.string.id_kind_uuid_time
        IdKind.SHORT_CODE -> R.string.id_kind_short_code
    }
