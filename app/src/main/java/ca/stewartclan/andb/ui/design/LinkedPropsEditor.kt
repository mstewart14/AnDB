package ca.stewartclan.andb.ui.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.LinkProps
import ca.stewartclan.andb.data.LinkedProps
import ca.stewartclan.andb.data.canBeColumn
import kotlinx.serialization.json.Json

val LinkedPropsSaver = Saver<LinkedProps, String>(
    save = { Json.encodeToString(LinkedProps.serializer(), it) },
    restore = { Json.decodeFromString(LinkedProps.serializer(), it) }
)

/** Which record link a Linked field shows through, and which field of the linked record it shows. */
@Composable
fun LinkedPropsEditor(props: LinkedProps, onChange: (LinkedProps) -> Unit, lookup: LookupContext, modifier: Modifier) {
    val links = lookup.currentFields.filter { it.recordLink != null }
    Column(modifier) {
        Text(stringResource(R.string.field_linked_explained), style = MaterialTheme.typography.bodyMedium)
        if (links.isEmpty()) {
            Text(
                stringResource(R.string.field_linked_no_links),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
            return
        }
        val link = links.find { it.id == props.linkFieldId }
        Picker(
            label = stringResource(R.string.field_linked_link),
            selected = link?.name,
            options = links.map { it.id to it.name },
            onSelect = { onChange(LinkedProps(linkFieldId = it)) },
            modifier = Modifier.padding(top = 8.dp)
        )
        val target = link?.recordLink?.databaseId ?: return
        LaunchedEffect(target) { lookup.onChange(target, null) }
        val fields = lookup.info.fields.orEmpty().filter { it.canBeColumn && it.props !is LinkProps }
        Picker(
            label = stringResource(R.string.field_linked_target),
            selected = fields.find { it.id == props.targetFieldId }?.name ?: fields.firstOrNull()?.name,
            options = fields.map { it.id to it.name },
            onSelect = { onChange(props.copy(targetFieldId = it)) },
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
