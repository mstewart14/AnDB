package ca.stewartclan.andb.ui.common

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import androidx.core.net.toUri
import ca.stewartclan.andb.data.LinkValue
import ca.stewartclan.andb.data.RecordRepository
import ca.stewartclan.andb.data.StoredPhoto
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Photos linked from records: lasting access to them, their snapshot (name and thumbnail) and
 * loading them for viewing, wherever they are: in the gallery, copied into a database, or picked
 * and not saved yet. Photos come from the system photo picker, which needs no permission.
 */
class PhotoAccess(private val resolver: ContentResolver, private val records: RecordRepository) {

    /** Keeps read access to picked photo [uri] and makes its link value, snapshot included. */
    suspend fun link(uri: Uri): LinkValue = withContext(Dispatchers.IO) {
        resolver.keepReadAccess(uri)
        val thumbnail = decode(uri, THUMBNAIL_PX)?.jpeg(THUMBNAIL_QUALITY)
        LinkValue(uri.toString(), name(uri), thumbnail)
    }

    /**
     * A copy of picked photo [uri] to store in the database: scaled to at most [COPY_PX] on its
     * longer side, carried as the link's [LinkValue.photo] until the record is saved. Null if the
     * photo can't be read.
     */
    suspend fun copy(uri: Uri): LinkValue? = withContext(Dispatchers.IO) {
        val bitmap = decode(uri, COPY_PX) ?: return@withContext null
        LinkValue(
            StoredPhoto.PENDING,
            name(uri),
            bitmap.scaled(THUMBNAIL_PX).jpeg(THUMBNAIL_QUALITY),
            bitmap.jpeg(COPY_QUALITY)
        )
    }

    /** Whether [link]'s photo (in database [databaseId] if stored there) can still be read. */
    suspend fun isAvailable(link: LinkValue, databaseId: String): Boolean = withContext(Dispatchers.IO) {
        when {
            link.photo != null -> true
            StoredPhoto.isStored(link.uri) -> records.storedPhoto(databaseId, link.uri) != null
            else -> resolver.canRead(link.uri.toUri())
        }
    }

    /**
     * [link]'s photo (in database [databaseId] if stored there) scaled to fit [maxPx] on its
     * longer side, or null if it can't be read.
     */
    suspend fun load(link: LinkValue, databaseId: String, maxPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val jpeg = link.photo ?: if (StoredPhoto.isStored(link.uri)) records.storedPhoto(databaseId, link.uri) else null
        val bitmap = if (jpeg != null) decodeBytes(jpeg, maxPx) else decode(link.uri.toUri(), maxPx)
        bitmap?.asImageBitmap()
    }

    private fun name(uri: Uri): String = resolver.displayName(uri) ?: uri.lastPathSegment.orEmpty()

    /** A stored copy, already upright and no larger than [COPY_PX], subsampled to about [maxPx]. */
    private fun decodeBytes(jpeg: ByteArray, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, sampled(bounds, maxPx))
    }

    /** Decoding at the largest power-of-two reduction that keeps an image of [bounds] at least [maxPx]. */
    private fun sampled(bounds: BitmapFactory.Options, maxPx: Int): BitmapFactory.Options {
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        return BitmapFactory.Options().apply { inSampleSize = sample }
    }

    private fun Bitmap.jpeg(quality: Int): ByteArray = ByteArrayOutputStream().use { out ->
        compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.toByteArray()
    }

    /** This bitmap no larger than [maxPx] on its longer side. */
    private fun Bitmap.scaled(maxPx: Int): Bitmap {
        val longer = maxOf(width, height)
        if (longer <= maxPx) return this
        val factor = maxPx.toFloat() / longer
        return scale((width * factor).toInt(), (height * factor).toInt())
    }

    /** Decodes [uri] no larger than [maxPx] on its longer side, upright; null if it can't be read. */
    private fun decode(uri: Uri, maxPx: Int): Bitmap? = try {
        if (!uri.isContent) {
            null
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
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
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, sampled(bounds, maxPx)) }
    }

    private companion object {
        const val TAG = "PhotoAccess"
        const val THUMBNAIL_PX = 192
        const val THUMBNAIL_QUALITY = 80

        /** Longest side of a photo copied into a database, and its JPEG quality. */
        const val COPY_PX = 2048
        const val COPY_QUALITY = 90
    }
}

/** [jpeg] thumbnail bytes as an image, decoded once per byte array; null if there are none. */
@Composable
fun rememberThumbnail(jpeg: ByteArray?): ImageBitmap? = remember(jpeg) {
    jpeg?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
}
