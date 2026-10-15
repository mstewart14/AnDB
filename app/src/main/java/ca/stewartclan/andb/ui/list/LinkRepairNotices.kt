package ca.stewartclan.andb.ui.list

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.FieldDef

/** Says which Link fields were just pointed at a new database of their deleted one's name. */
@Composable
fun RepairedSnackbar(viewModel: RecordListViewModel, snackbarHostState: SnackbarHostState) {
    val repaired by viewModel.repaired.collectAsStateWithLifecycle()
    val message = repaired.map { r ->
        val total = r.plan.links.size + r.plan.notFound + r.plan.ambiguous
        pluralStringResource(
            R.plurals.list_link_repaired,
            total,
            r.field.name,
            r.databaseName,
            r.plan.links.size,
            total
        )
    }.joinToString("\n").takeIf { it.isNotEmpty() }
    LaunchedEffect(message) {
        if (message != null) {
            viewModel.repairedShown()
            snackbarHostState.showSnackbar(message)
        }
    }
}

/** A Link [field] whose database was deleted, with a button to choose its replacement. */
@Composable
fun BrokenLinkRow(field: FieldDef, onFix: (FieldDef) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            stringResource(R.string.list_link_broken, field.name),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { onFix(field) }) { Text(stringResource(R.string.list_link_fix)) }
    }
}
