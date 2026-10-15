package ca.stewartclan.andb.ui.common

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import ca.stewartclan.andb.R

/** Text field state starting with [initialName] selected, so typing replaces it. */
@Composable
fun rememberNameState(initialName: String): MutableState<TextFieldValue> =
    rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialName, TextRange(0, initialName.length)))
    }

/** A focused, single-line name input that shows [problem] once the user has typed something. */
@Composable
fun NameField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    problem: NameProblem,
    duplicateMessage: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    // A blank name is only an error once the user has typed something and cleared it again.
    val showError = problem != NameProblem.OK && !(problem == NameProblem.BLANK && value.text.isEmpty())
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.name_label)) },
        singleLine = true,
        isError = showError,
        supportingText = if (showError) {
            {
                Text(
                    when (problem) {
                        NameProblem.BLANK -> stringResource(R.string.name_blank)
                        NameProblem.TOO_LONG -> stringResource(R.string.name_too_long, NameValidator.MAX_LENGTH)
                        NameProblem.DUPLICATE -> duplicateMessage
                        NameProblem.OK -> ""
                    }
                )
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { if (problem == NameProblem.OK) onDone() }),
        modifier = modifier.focusRequester(focus)
    )
}
