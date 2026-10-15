package ca.stewartclan.andb.ui.record

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.stewartclan.andb.R

/** Opening linked records and adding records already linked to this one. */
class LinkActions(
    /** Opens record (database id, record id). */
    val onOpen: (String, Long) -> Unit,
    /** Starts a new record in [BacklinkGroup.databaseId], linked here through its field. */
    val onAdd: (BacklinkGroup) -> Unit
)

/**
 * "Linked from": for each Link field (in any database) that can link to this record, the records
 * that do, each opening on tap, and a button to add one already linked here.
 */
@Composable
fun BacklinksSection(groups: List<BacklinkGroup>, readOnly: Boolean, actions: LinkActions) {
    if (groups.isEmpty()) return
    Column(Modifier.padding(top = 16.dp)) {
        Text(
            stringResource(R.string.backlinks_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        HorizontalDivider(Modifier.padding(top = 4.dp))
        groups.forEach { group -> BacklinkGroupView(group, readOnly, actions) }
    }
}

@Composable
private fun BacklinkGroupView(group: BacklinkGroup, readOnly: Boolean, actions: LinkActions) {
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.backlinks_group, group.databaseName, group.field.name, group.records.size),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            if (!readOnly) {
                TextButton(onClick = { actions.onAdd(group) }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text(stringResource(R.string.backlinks_add))
                }
            }
        }
        group.records.forEach { (recordId, title) ->
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { actions.onOpen(group.databaseId, recordId) }
                    .padding(vertical = 8.dp)
            )
        }
    }
}
