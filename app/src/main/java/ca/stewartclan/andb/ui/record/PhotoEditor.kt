package ca.stewartclan.andb.ui.record

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ca.stewartclan.andb.AnDbApplication
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.LinkProps
import ca.stewartclan.andb.data.LinkValue
import ca.stewartclan.andb.ui.common.PhotoAccess
import ca.stewartclan.andb.ui.common.rememberThumbnail
import kotlinx.coroutines.launch

/**
 * A photo link: its thumbnail and name, with buttons to choose (or choose again) and remove. The
 * thumbnail opens the photo full screen. If the photo can't be read any more, the saved snapshot
 * still shows, marked unavailable. A field that copies photos copies the one chosen.
 */
@Composable
fun PhotoEditor(field: FieldDef, ctx: EditorContext, modifier: Modifier) {
    val name = field.name
    val link = ctx.state.links[field.id]
    val readOnly = ctx.readOnly
    val onLink = { value: LinkValue? -> ctx.onLink(field, value) }
    val copies = (field.props as? LinkProps)?.copyPhotos == true
    val photos = rememberPhotoAccess()
    val scope = rememberCoroutineScope()
    var linking by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            linking = true
            scope.launch {
                onLink(if (copies) photos.copy(uri) ?: photos.link(uri) else photos.link(uri))
                linking = false
            }
        }
    }
    val available by produceState<Boolean?>(null, link) { value = link?.let { photos.isAvailable(it, ctx.databaseId) } }

    Column(modifier) {
        Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Thumbnail(link, linking, onClick = { if (link != null) viewing = true })
            Column(Modifier.padding(start = 12.dp)) {
                if (link != null) {
                    Text(link.name, style = MaterialTheme.typography.bodyMedium)
                    if (available == false) {
                        Text(
                            stringResource(R.string.photo_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                if (!readOnly) {
                    PhotoButtons(link, available, onChoose = {
                        picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                    }, onRemove = { onLink(null) })
                }
            }
        }
    }
    if (viewing && link != null) PhotoViewer(link, photos, ctx.databaseId, onClose = { viewing = false })
}

/** Choose (or choose again, or change) the photo, and remove it. */
@Composable
private fun PhotoButtons(link: LinkValue?, available: Boolean?, onChoose: () -> Unit, onRemove: () -> Unit) {
    Row {
        TextButton(onClick = onChoose) {
            Text(
                stringResource(
                    when {
                        link == null -> R.string.photo_choose
                        available == false -> R.string.photo_choose_again
                        else -> R.string.photo_change
                    }
                )
            )
        }
        if (link != null) {
            TextButton(onClick = onRemove) { Text(stringResource(R.string.action_remove)) }
        }
    }
}

@Composable
private fun Thumbnail(link: LinkValue?, linking: Boolean, onClick: () -> Unit) {
    val image = rememberThumbnail(link?.thumbnail)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(THUMBNAIL_SIZE)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = link != null, onClick = onClick)
    ) {
        when {
            linking -> CircularProgressIndicator()
            image != null -> Image(
                image,
                contentDescription = link?.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * The photo full screen (from database [databaseId] if copied there); the saved thumbnail stands in
 * if the photo can't be read. Tap to close.
 */
@Composable
fun PhotoViewer(link: LinkValue, photos: PhotoAccess, databaseId: String, onClose: () -> Unit) {
    val screenPx = LocalWindowInfo.current.containerSize.let { maxOf(it.width, it.height) }
    val thumbnail = rememberThumbnail(link.thumbnail)
    val full by produceState<ImageBitmap?>(null, link) { value = photos.load(link, databaseId, screenPx) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onClose)
        ) {
            val image = full ?: thumbnail
            if (image != null) {
                Image(
                    image,
                    contentDescription = link.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun rememberPhotoAccess(): PhotoAccess {
    val context = LocalContext.current
    return remember(context) { (context.applicationContext as AnDbApplication).photos }
}

private val THUMBNAIL_SIZE = 96.dp
