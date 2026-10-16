package ca.stewartclan.andb.ui.record

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.FieldDef
import kotlin.math.roundToInt

@Composable
internal fun SectionHeader(name: String, modifier: Modifier) {
    Column(modifier.padding(top = 8.dp)) {
        Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}

@Composable
internal fun ReadOnlyValue(name: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifEmpty { "–" }, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun CheckboxEditor(
    name: String,
    checked: Boolean,
    readOnly: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !readOnly) { onChange(!checked) }
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = !readOnly, modifier = Modifier.padding(12.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun RatingEditor(name: String, rating: Int, readOnly: Boolean, onChange: (Int) -> Unit, modifier: Modifier) {
    Column(modifier) {
        Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row {
            for (star in 1..FieldValues.MAX_RATING) {
                // Tapping the current rating again clears it.
                IconButton(onClick = { onChange(if (star == rating) 0 else star) }, enabled = !readOnly) {
                    Icon(
                        if (star <= rating) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = pluralStringResource(R.plurals.record_rating_star, star, star),
                        tint = if (star <=
                            rating
                        ) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun ScaleEditor(name: String, value: Int?, readOnly: Boolean, onChange: (Int?) -> Unit, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$name: ${value ?: "–"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (value != null && !readOnly) {
                TextButton(onClick = { onChange(null) }) { Text(stringResource(R.string.record_clear)) }
            }
        }
        Slider(
            value = (value ?: 0).toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..FieldValues.MAX_SCALE.toFloat(),
            steps = FieldValues.MAX_SCALE - 1,
            enabled = !readOnly
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListDropdown(
    name: String,
    text: String,
    choices: List<String>,
    readOnly: Boolean,
    onText: (String) -> Unit,
    modifier: Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    // A stored value no longer among the choices is still shown and kept.
    val options = listOf("") + choices + listOfNotNull(text.takeIf { it.isNotEmpty() && it !in choices })
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (!readOnly) expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {},
            readOnly = true,
            enabled = !readOnly,
            singleLine = true,
            label = { Text(name) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !readOnly)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.ifEmpty { stringResource(R.string.record_none) }) },
                    onClick = {
                        onText(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChoiceChips(field: FieldDef, ctx: EditorContext, modifier: Modifier) {
    val chosen = ctx.state.chosen[field.id].orEmpty()
    val choices = ctx.state.listChoices[field.id].orEmpty()
    Column(modifier) {
        FieldLabel(field.name)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (choices + chosen.filter { it !in choices }).forEach { choice ->
                FilterChip(
                    selected = choice in chosen,
                    onClick = { ctx.onToggleChoice(field, choice) },
                    label = { Text(choice) },
                    enabled = !ctx.readOnly
                )
            }
        }
    }
}

/** The name above a field's value, for editors that aren't text boxes with their own label. */
@Composable
internal fun FieldLabel(name: String) {
    Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
