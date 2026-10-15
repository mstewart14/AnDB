package ca.stewartclan.andb.ui.record

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import ca.stewartclan.andb.ui.common.Facet

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
    // A link left from a database the field linked to before has no title here.
    val title = field.linkedRecordId(link)?.let { titles[it] }
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
            facets = ctx.state.linkFacets[field.id].orEmpty(),
            onPick = { recordId ->
                ctx.onLink(field, LinkValue(RecordRef(databaseId, recordId).uri, titles.getValue(recordId), null))
                choosing = false
            },
            onDismiss = { choosing = false }
        )
    }
}

/**
 * A searchable list of records, by title, to pick one. Each of [facets] gets a menu to show only
 * the records with one of its values, and their values are shown under each title.
 */
@Composable
private fun RecordChooser(
    title: String,
    titles: Map<Long, String>,
    facets: List<Facet>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var narrowed by remember { mutableStateOf(mapOf<Long, String>()) }
    val shown = titles.entries
        .filter { (recordId, text) ->
            text.contains(search.trim(), ignoreCase = true) && facets.all { it.admits(narrowed, recordId) }
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.value })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                FacetMenus(facets, titles.keys, narrowed, onNarrow = { narrowed = it })
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
                        val groups = facets.flatMap { it.byRecord[recordId].orEmpty() }.joinToString(" · ")
                        ChoiceRow(text, groups, onClick = { onPick(recordId) })
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/** A record's [title], with its facet values ([groups]) below it in small print. */
@Composable
private fun ChoiceRow(title: String, groups: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (groups.isNotEmpty()) {
            Text(
                groups,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A menu for each of [facets] narrowing records [recordIds] to one of its values; [narrowed] holds
 * the value chosen for each facet that has one, by field id.
 */
@Composable
private fun FacetMenus(
    facets: List<Facet>,
    recordIds: Set<Long>,
    narrowed: Map<Long, String>,
    onNarrow: (Map<Long, String>) -> Unit
) {
    if (facets.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        facets.forEach { facet ->
            // Only values some record still has, given the other facets' choices.
            val others = narrowed - facet.fieldId
            val offered = facet.values.filter { value ->
                recordIds.any { id -> value in facet.byRecord[id].orEmpty() && facets.all { it.admits(others, id) } }
            }
            FacetMenu(facet.name, narrowed[facet.fieldId], offered) { value ->
                onNarrow(if (value == null) others else others + (facet.fieldId to value))
            }
        }
    }
}

/** A chip naming facet [name] and its [chosen] value ("Any" if none), opening a menu of [values]. */
@Composable
private fun FacetMenu(name: String, chosen: String?, values: List<String>, onChoose: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val any = stringResource(R.string.record_link_any)
    Box {
        FilterChip(
            selected = chosen != null,
            onClick = { open = true },
            label = { Text(stringResource(R.string.record_link_facet, name, chosen ?: any)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(any) }, onClick = {
                onChoose(null)
                open = false
            })
            values.forEach { value ->
                DropdownMenuItem(text = { Text(value) }, onClick = {
                    onChoose(value)
                    open = false
                })
            }
        }
    }
}

/** Whether record [recordId] has the value [narrowed] holds for this facet, if any. */
private fun Facet.admits(narrowed: Map<Long, String>, recordId: Long): Boolean =
    narrowed[fieldId]?.let { it in byRecord[recordId].orEmpty() } ?: true

private val CHOOSER_HEIGHT = 360.dp
