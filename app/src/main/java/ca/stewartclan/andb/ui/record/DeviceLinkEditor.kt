package ca.stewartclan.andb.ui.record

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.PickContact
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ca.stewartclan.andb.AnDbApplication
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.LinkProps
import ca.stewartclan.andb.data.LinkSource
import ca.stewartclan.andb.data.LinkValue
import kotlinx.coroutines.launch

/**
 * A link to a contact or file: its name (tap to open it in its own app), with buttons to choose
 * (or choose again) and remove. A file that can't be read any more is marked unavailable.
 */
@Composable
fun DeviceLinkEditor(field: FieldDef, ctx: EditorContext, modifier: Modifier) {
    val source = (field.props as? LinkProps)?.source ?: return
    val context = LocalContext.current
    val links = remember(context) { (context.applicationContext as AnDbApplication).deviceLinks }
    val scope = rememberCoroutineScope()
    val link = ctx.state.links[field.id]
    val contactPicker = rememberLauncherForActivityResult(PickContact()) { uri ->
        if (uri != null) scope.launch { links.contact(uri)?.let { ctx.link(field, it) } }
    }
    val filePicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) scope.launch { ctx.link(field, links.file(uri)) }
    }
    val available by produceState<Boolean?>(null, link?.uri) { value = link?.let { links.isAvailable(source, it.uri) } }
    val cantOpen = stringResource(R.string.link_open_failed)

    Column(modifier) {
        FieldLabel(field.name)
        if (link != null) {
            LinkName(link, available) {
                if (!links.open(context, source, link)) Toast.makeText(context, cantOpen, Toast.LENGTH_SHORT).show()
            }
        }
        if (!ctx.readOnly) {
            LinkButtons(
                source,
                hasLink = link != null,
                onChoose = {
                    val contact = source == LinkSource.CONTACT
                    if (contact) contactPicker.launch(null) else filePicker.launch(arrayOf("*/*"))
                },
                onRemove = { ctx.link(field, null) }
            )
        }
    }
}

/** The linked item's name, underlined to show it opens; marked if it can't be reached. */
@Composable
private fun LinkName(link: LinkValue, available: Boolean?, onOpen: () -> Unit) {
    Text(
        link.name,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier
            .clickable(onClick = onOpen)
            .padding(vertical = 4.dp)
    )
    if (available == false) {
        Text(
            stringResource(R.string.file_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/** Choose (or change) the contact or file, and remove it. */
@Composable
private fun LinkButtons(source: LinkSource, hasLink: Boolean, onChoose: () -> Unit, onRemove: () -> Unit) {
    Row {
        TextButton(onClick = onChoose) {
            Text(
                stringResource(
                    when {
                        hasLink -> R.string.photo_change
                        source == LinkSource.CONTACT -> R.string.contact_choose
                        else -> R.string.file_choose
                    }
                )
            )
        }
        if (hasLink) {
            TextButton(onClick = onRemove) { Text(stringResource(R.string.action_remove)) }
        }
    }
}
