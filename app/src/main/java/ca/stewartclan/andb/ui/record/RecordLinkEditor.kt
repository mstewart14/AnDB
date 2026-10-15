package ca.stewartclan.andb.ui.record

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.LinkValue
import ca.stewartclan.andb.data.RecordRef

/**
 * A link to a record: the linked record's title (tap to open it), and buttons to choose another or
 * remove the link. A link whose record was deleted shows its saved title, marked deleted.
 */
@Composable
fun RecordLinkEditor(field: FieldDef, ctx: EditorContext, modifier: Modifier) {
    val databaseId = field.recordLink?.databaseId ?: return
    val link = ctx.state.links[field.id]
    val ref = link?.let { RecordRef.parse(it.uri) }
    val titles = ctx.state.recordTitles[field.id].orEmpty()
    val title = ref?.let { titles[it.recordId] }
    var choosing by rememberSaveable { mutableStateOf(false) }

    Column(modifier) {
        Text(
            field.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (link != null && ref != null) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .clickable { ctx.onOpenRecord(ref) }
                        .padding(vertical = 4.dp)
                )
            } else {
                Text(link.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.record_link_deleted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        if (!ctx.readOnly) {
            Row {
                TextButton(onClick = { choosing = true }) {
                    Text(stringResource(if (link == null) R.string.record_link_choose else R.string.photo_change))
                }
                if (link != null) {
                    TextButton(onClick = { ctx.onLink(field, null) }) { Text(stringResource(R.string.action_remove)) }
                }
            }
        }
    }
    if (choosing) {
        RecordChooser(
            title = field.name,
            titles = titles,
            onPick = { recordId ->
                ctx.onLink(field, LinkValue(RecordRef(databaseId, recordId).uri, titles.getValue(recordId), null))
                choosing = false
            },
            onDismiss = { choosing = false }
        )
    }
}

/** A searchable list of records, by title, to pick one. */
@Composable
private fun RecordChooser(title: String, titles: Map<Long, String>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    var search by remember { mutableStateOf("") }
    val shown = titles.entries
        .filter { it.value.contains(search.trim(), ignoreCase = true) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.value })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text(stringResource(R.string.find_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (shown.isEmpty()) {
                    Text(
                        stringResource(
                            if (titles.isEmpty()) R.string.record_link_none_to_choose else R.string.find_no_match
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                LazyColumn(Modifier.heightIn(max = CHOOSER_HEIGHT)) {
                    items(shown, key = { it.key }) { (recordId, text) ->
                        Text(
                            text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(recordId) }
                                .padding(vertical = 12.dp)
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

private val CHOOSER_HEIGHT = 360.dp
