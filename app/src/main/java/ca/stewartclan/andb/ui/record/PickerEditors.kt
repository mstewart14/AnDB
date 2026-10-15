package ca.stewartclan.andb.ui.record

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.FieldType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** A read-only text field that calls [onOpen] when tapped, with a clear button when set. */
@Composable
private fun PickerField(
    name: String,
    shown: String,
    readOnly: Boolean,
    onOpen: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier
) {
    // A text field consumes taps itself, so listen to its presses rather than adding clickable.
    val interactions = remember { MutableInteractionSource() }
    LaunchedEffect(interactions) {
        interactions.interactions.collect { if (it is PressInteraction.Release) onOpen() }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = shown,
            onValueChange = {},
            readOnly = true,
            enabled = !readOnly,
            label = { Text(name) },
            trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
            interactionSource = interactions,
            modifier = Modifier.weight(1f)
        )
        if (shown.isNotEmpty() && !readOnly) {
            TextButton(onClick = onClear) { Text(stringResource(R.string.record_clear)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateEditor(name: String, text: String, readOnly: Boolean, onText: (String) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val date = FieldValues.parseDate(text)
    PickerField(
        name,
        if (date != null) FieldValues.display(DATE_FIELD, text) else text,
        readOnly,
        onOpen = { open = true },
        onClear = { onText("") },
        modifier = modifier
    )
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(
                ZoneOffset.UTC
            ).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onText(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    open = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } }
        ) { DatePicker(state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeEditor(name: String, text: String, readOnly: Boolean, onText: (String) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val time = FieldValues.parseTime(text)
    PickerField(
        name,
        if (time != null) FieldValues.display(TIME_FIELD, text) else text,
        readOnly,
        onOpen = { open = true },
        onClear = { onText("") },
        modifier = modifier
    )
    if (open) {
        val start = time ?: LocalTime.now()
        val state = rememberTimePickerState(start.hour, start.minute)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onText(LocalTime.of(state.hour, state.minute).toString())
                    open = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
            text = { TimePicker(state) }
        )
    }
}

private val DATE_FIELD = FieldDef(0, 0, "", FieldType.DATE)
private val TIME_FIELD = FieldDef(0, 0, "", FieldType.TIME)
