package ca.stewartclan.andb.ui.chooser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.DatabaseSummary
import ca.stewartclan.andb.ui.common.NameField
import ca.stewartclan.andb.ui.common.NameProblem
import ca.stewartclan.andb.ui.common.rememberNameState

/** The [DuplicateDialog] for [db], duplicating it through [viewModel]. */
@Composable
fun DuplicateDatabase(db: DatabaseSummary, viewModel: ChooserViewModel, onClose: () -> Unit) {
    val copyOf = stringResource(R.string.chooser_copy_name)
    DuplicateDialog(
        initialName = remember(db.id) { viewModel.copyName(db) { copyOf.format(it) } },
        check = viewModel::checkName,
        onConfirm = { name, withRecords ->
            viewModel.duplicate(db, name, withRecords)
            onClose()
        },
        onDismiss = onClose
    )
}

/** Names the copy of a database and chooses whether its records come along; [onConfirm] gets both. */
@Composable
private fun DuplicateDialog(
    initialName: String,
    check: (String) -> NameProblem,
    onConfirm: (name: String, withRecords: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var value by rememberNameState(initialName)
    var withRecords by rememberSaveable { mutableStateOf(false) }
    val problem = check(value.text)
    val confirm = { if (problem == NameProblem.OK) onConfirm(value.text, withRecords) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chooser_duplicate_database)) },
        text = {
            Column {
                NameField(
                    value = value,
                    onValueChange = { value = it },
                    problem = problem,
                    duplicateMessage = stringResource(R.string.chooser_name_duplicate),
                    onDone = confirm
                )
                CopyChoice(
                    stringResource(R.string.chooser_duplicate_design_only),
                    stringResource(R.string.chooser_duplicate_design_only_explained),
                    selected = !withRecords,
                    onSelect = { withRecords = false }
                )
                CopyChoice(
                    stringResource(R.string.chooser_duplicate_with_records),
                    stringResource(R.string.chooser_duplicate_with_records_explained),
                    selected = withRecords,
                    onSelect = { withRecords = true }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = confirm, enabled = problem == NameProblem.OK) {
                Text(stringResource(R.string.action_duplicate_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
fun CopyChoice(label: String, explanation: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(top = 8.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
