package ca.stewartclan.andb.ui.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.LinkProps
import ca.stewartclan.andb.data.LinkSource
import ca.stewartclan.andb.ui.common.RecordTitles
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Link settings as edited. */
@Serializable
data class LinkDraft(
    val source: LinkSource = LinkSource.PHOTO,
    val databaseId: String? = null,
    val displayFieldId: Long? = null,
    /** Kept while [databaseId] is still the deleted database it names. */
    val missingName: String? = null,
    val copyPhotos: Boolean = false,
    val multiple: Boolean = false
) {
    /** The settings, or null while a record link has no database chosen. */
    fun toProps(): LinkProps? = when (source) {
        LinkSource.PHOTO -> LinkProps(LinkSource.PHOTO, copyPhotos = copyPhotos)
        LinkSource.RECORD -> databaseId?.let {
            LinkProps(LinkSource.RECORD, it, displayFieldId, missingName, multiple = multiple)
        }
        LinkSource.CONTACT, LinkSource.FILE -> LinkProps(source)
    }

    companion object {
        fun of(props: LinkProps) = LinkDraft(
            props.source,
            props.databaseId,
            props.displayFieldId,
            props.missingName,
            props.copyPhotos,
            props.multiple
        )

        val Saver = Saver<LinkDraft, String>(
            save = { Json.encodeToString(serializer(), it) },
            restore = { Json.decodeFromString(serializer(), it) }
        )
    }
}

@Composable
fun LinkPropsEditor(draft: LinkDraft, onDraftChange: (LinkDraft) -> Unit, lookup: LookupContext, modifier: Modifier) {
    Column(modifier) {
        Text(stringResource(R.string.link_source_label), style = MaterialTheme.typography.labelMedium)
        val sources = listOf(
            LinkSource.PHOTO to R.string.link_source_photo,
            LinkSource.RECORD to R.string.link_source_record,
            LinkSource.CONTACT to R.string.link_source_contact,
            LinkSource.FILE to R.string.link_source_file
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            sources.forEachIndexed { i, (source, label) ->
                SegmentedButton(
                    selected = draft.source == source,
                    onClick = { onDraftChange(draft.copy(source = source)) },
                    shape = SegmentedButtonDefaults.itemShape(i, sources.size)
                ) { Text(stringResource(label)) }
            }
        }
        val explanation = when (draft.source) {
            LinkSource.PHOTO -> R.string.link_photo_explained
            LinkSource.RECORD -> R.string.link_record_explained
            LinkSource.CONTACT -> R.string.link_contact_explained
            LinkSource.FILE -> R.string.link_file_explained
        }
        Text(
            stringResource(explanation),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (draft.source == LinkSource.RECORD) {
            RecordTarget(draft, onDraftChange, lookup)
            SwitchRow(
                stringResource(R.string.link_multiple),
                stringResource(R.string.link_multiple_explained),
                checked = draft.multiple,
                onCheckedChange = { onDraftChange(draft.copy(multiple = it)) }
            )
        }
        if (draft.source == LinkSource.PHOTO) {
            SwitchRow(
                stringResource(R.string.link_copy_photos),
                stringResource(R.string.link_copy_photos_explained),
                checked = draft.copyPhotos,
                onCheckedChange = { onDraftChange(draft.copy(copyPhotos = it)) }
            )
        }
    }
}

/** A setting turned on or off: its [title], what it does, and the switch. */
@Composable
private fun SwitchRow(title: String, explanation: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Which database a record link points into, and which of its fields shows the linked record. */
@Composable
private fun RecordTarget(draft: LinkDraft, onDraftChange: (LinkDraft) -> Unit, lookup: LookupContext) {
    LaunchedEffect(draft.databaseId) { lookup.onChange(draft.databaseId, null) }
    val gone = draft.databaseId != null && lookup.databases.none { it.id == draft.databaseId }
    if (gone) {
        Text(
            draft.missingName?.let { stringResource(R.string.link_database_deleted_named, it) }
                ?: stringResource(R.string.link_database_deleted),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
    Picker(
        label = stringResource(R.string.link_database),
        selected = lookup.databases.find { it.id == draft.databaseId }?.name,
        options = lookup.databases.map { it.id to it.name },
        // The display field is worked out afresh for the new database (see RelinkPreview).
        onSelect = { onDraftChange(draft.copy(databaseId = it, displayFieldId = null, missingName = null)) },
        modifier = Modifier.padding(top = 8.dp)
    )
    if (draft.databaseId == null || gone) return
    val fields = lookup.info.fields.orEmpty()
    val candidates = fields.filter { it == RecordTitles.displayField(fields, it.id) && it.props !is LinkProps }
    Picker(
        label = stringResource(R.string.link_display_field),
        selected = RecordTitles.displayField(fields, draft.displayFieldId)?.name,
        options = candidates.map { it.id to it.name },
        onSelect = { onDraftChange(draft.copy(displayFieldId = it)) },
        modifier = Modifier.padding(top = 8.dp)
    )
}
