package ca.stewartclan.andb.ui.common

import android.content.ContentResolver
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import ca.stewartclan.andb.data.LinkValue
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Photos linked from records: lasting access to them, their snapshot (name and thumbnail) and
 * loading them for viewing. Photos come from the system photo picker, which needs no permission.
 */
class PhotoAccess(private val resolver: ContentResolver) {

    /** Keeps read access to picked photo [uri] and makes its link value, snapshot included. */
    suspend fun link(uri: Uri): LinkValue = withContext(Dispatchers.IO) {
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // Still readable until the app closes; the snapshot keeps the record showing after that.
            Log.w(TAG, "No lasting access to $uri", e)
        }
        val thumbnail = decode(uri, THUMBNAIL_PX)?.let { bitmap ->
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, out)
                out.toByteArray()
            }
        }
        LinkValue(uri.toString(), displayName(uri) ?: uri.lastPathSegment.orEmpty(), thumbnail)
    }

    /** Whether linked photo [uri] can still be read (not deleted, access not revoked). */
    suspend fun isAvailable(uri: String): Boolean = withContext(Dispatchers.IO) {
        try {
            resolver.openInputStream(uri.toUri())?.use { true } ?: false
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    /** Photo [uri] scaled to fit [maxPx] on its longer side, or null if it can't be read. */
    suspend fun load(uri: String, maxPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        decode(uri.toUri(), maxPx)?.asImageBitmap()
    }

    private fun displayName(uri: Uri): String? = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: SecurityException) {
        null
    }

    /** Decodes [uri] no larger than [maxPx] on its longer side, upright; null if it can't be read. */
    private fun decode(uri: Uri, maxPx: Int): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies the photo's EXIF rotation.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val longer = maxOf(info.size.width, info.size.height)
                if (longer > maxPx) {
                    val scale = maxPx.toFloat() / longer
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            decodeSampled(uri, maxPx)
        }
    } catch (e: IOException) {
        Log.w(TAG, "Can't read photo $uri", e)
        null
    } catch (e: SecurityException) {
        Log.w(TAG, "No access to photo $uri", e)
        null
    }

    /** Before Android 9: subsampled, without EXIF rotation. */
    private fun decodeSampled(uri: Uri, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private companion object {
        const val TAG = "PhotoAccess"
        const val THUMBNAIL_PX = 192
        const val THUMBNAIL_QUALITY = 80
    }
}

/** [jpeg] thumbnail bytes as an image, decoded once per byte array; null if there are none. */
@Composable
fun rememberThumbnail(jpeg: ByteArray?): ImageBitmap? = remember(jpeg) {
    jpeg?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
}
