package ca.stewartclan.andb.ui.chooser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.DatabaseSummary
import ca.stewartclan.andb.data.Template
import ca.stewartclan.andb.data.Templates
import ca.stewartclan.andb.ui.common.NameField
import ca.stewartclan.andb.ui.common.NameProblem
import ca.stewartclan.andb.ui.common.rememberNameState
import ca.stewartclan.andb.ui.design.Picker

/** What a new database starts with. */
sealed interface StartFrom {
    data object Blank : StartFrom

    data class FromTemplate(val template: Template) : StartFrom

    /** The design (fields, list views, filters) of database [databaseId], without its records. */
    data class DesignOf(val databaseId: String) : StartFrom
}

/** [StartFrom] as a key that survives rotation: "blank", "template:<id>" or "design:<database id>". */
private fun StartFrom.key(): String = when (this) {
    StartFrom.Blank -> BLANK
    is StartFrom.FromTemplate -> "$TEMPLATE${template.id}"
    is StartFrom.DesignOf -> "$DESIGN$databaseId"
}

private fun startFrom(key: String): StartFrom = when {
    key.startsWith(TEMPLATE) -> Templates.find(key.removePrefix(TEMPLATE))?.let { StartFrom.FromTemplate(it) }
    key.startsWith(DESIGN) -> StartFrom.DesignOf(key.removePrefix(DESIGN))
    else -> null
} ?: StartFrom.Blank

private const val BLANK = "blank"
private const val TEMPLATE = "template:"
private const val DESIGN = "design:"

/**
 * Names a new database and chooses what it starts from: nothing, a built-in template, or the
 * design of one of [databases]. [onConfirm] gets both.
 */
@Composable
fun NewDatabaseDialog(
    databases: List<DatabaseSummary>,
    check: (String) -> NameProblem,
    onConfirm: (name: String, start: StartFrom) -> Unit,
    onDismiss: () -> Unit
) {
    var value by rememberNameState("")
    var startKey by rememberSaveable { mutableStateOf(BLANK) }
    val problem = check(value.text)
    val confirm = { if (problem == NameProblem.OK) onConfirm(value.text, startFrom(startKey)) }

    val options = listOf(BLANK to stringResource(R.string.chooser_start_blank)) +
        Templates.all.map {
            StartFrom.FromTemplate(it).key() to stringResource(R.string.chooser_start_template, it.name)
        } +
        databases.map { StartFrom.DesignOf(it.id).key() to stringResource(R.string.chooser_start_design, it.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chooser_new_database)) },
        text = {
            Column {
                NameField(
                    value = value,
                    onValueChange = { value = it },
                    problem = problem,
                    duplicateMessage = stringResource(R.string.chooser_name_duplicate),
                    onDone = confirm
                )
                Picker(
                    label = stringResource(R.string.chooser_start_from),
                    selected = options.find { it.first == startKey }?.second,
                    options = options,
                    onSelect = { startKey = it },
                    modifier = Modifier.padding(top = 8.dp)
                )
                startDescription(startFrom(startKey))?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = confirm, enabled = problem == NameProblem.OK) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/** What the database will start with: a template's fields, or a note that the design is copied. */
@Composable
private fun startDescription(start: StartFrom): String? = when (start) {
    StartFrom.Blank -> null
    is StartFrom.FromTemplate -> stringResource(
        R.string.chooser_start_fields,
        start.template.fields.joinToString(", ") {
            it.name
        }
    )
    is StartFrom.DesignOf -> stringResource(R.string.chooser_start_design_explained)
}
