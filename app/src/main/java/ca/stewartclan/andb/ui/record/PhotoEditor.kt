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
import ca.stewartclan.andb.data.LinkValue
import ca.stewartclan.andb.ui.common.PhotoAccess
import ca.stewartclan.andb.ui.common.rememberThumbnail
import kotlinx.coroutines.launch

/**
 * A photo link: its thumbnail and name, with buttons to choose (or choose again) and remove. The
 * thumbnail opens the photo full screen. If the photo can't be read any more, the saved snapshot
 * still shows, marked unavailable.
 */
@Composable
fun PhotoEditor(name: String, link: LinkValue?, readOnly: Boolean, onLink: (LinkValue?) -> Unit, modifier: Modifier) {
    val photos = rememberPhotoAccess()
    val scope = rememberCoroutineScope()
    var linking by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            linking = true
            scope.launch {
                onLink(photos.link(uri))
                linking = false
            }
        }
    }
    val available by produceState<Boolean?>(null, link?.uri) { value = link?.let { photos.isAvailable(it.uri) } }

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
                    Row {
                        TextButton(onClick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }) {
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
                            TextButton(onClick = { onLink(null) }) { Text(stringResource(R.string.action_remove)) }
                        }
                    }
                }
            }
        }
    }
    if (viewing && link != null) PhotoViewer(link, photos, onClose = { viewing = false })
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

/** The photo full screen; the saved thumbnail stands in if the photo can't be read. Tap to close. */
@Composable
private fun PhotoViewer(link: LinkValue, photos: PhotoAccess, onClose: () -> Unit) {
    val screenPx = LocalWindowInfo.current.containerSize.let { maxOf(it.width, it.height) }
    val thumbnail = rememberThumbnail(link.thumbnail)
    val full by produceState<ImageBitmap?>(null, link.uri) { value = photos.load(link.uri, screenPx) }
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
private fun rememberPhotoAccess(): PhotoAccess {
    val context = LocalContext.current
    return remember(context) { (context.applicationContext as AnDbApplication).photos }
}

private val THUMBNAIL_SIZE = 96.dp
