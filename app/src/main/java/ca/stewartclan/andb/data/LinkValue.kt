package ca.stewartclan.andb.data

/**
 * The value of a Link field: [uri] of the linked item, plus a snapshot kept in the database so the
 * record still shows something if the item goes away: its [name] and, for photos, a small JPEG
 * [thumbnail]. A photo being copied into the database carries its JPEG as [photo] until the record
 * is saved, which stores it and gives it a [StoredPhoto] uri.
 */
class LinkValue(val uri: String, val name: String, val thumbnail: ByteArray?, val photo: ByteArray? = null) {
    override fun equals(other: Any?): Boolean = other is LinkValue &&
        other.uri == uri &&
        other.name == name &&
        other.thumbnail.contentEquals(thumbnail) &&
        other.photo.contentEquals(photo)

    override fun hashCode(): Int =
        31 * (31 * (31 * uri.hashCode() + name.hashCode()) + thumbnail.contentHashCode()) + photo.contentHashCode()

    override fun toString(): String = name
}

/**
 * What pointing a Link field to records at another database carries over: [links] matched in the
 * new database, by record id (records not in it keep their old link, shown as unavailable; null
 * clears every link instead), and the new target field of each Linked field showing through it,
 * by Linked field id (null for the new database's display field).
 */
data class Repoint(val links: Map<Long, LinkValue>?, val linkedTargets: Map<Long, Long?>)

/**
 * Photos copied into a database, kept in its `andb_photo` table. A link to one is `andb-photo:<id>`,
 * relative to the database it's in, so copies of the database keep their photos.
 */
object StoredPhoto {
    private const val SCHEME = "andb-photo:"

    /** The uri of a photo not saved yet (see [LinkValue.photo]). */
    const val PENDING = SCHEME

    fun uri(id: Long): String = "$SCHEME$id"

    /** The stored photo's id, or null if [uri] isn't a saved stored photo. */
    fun id(uri: String?): Long? = uri?.takeIf { it.startsWith(SCHEME) }?.removePrefix(SCHEME)?.toLongOrNull()

    fun isStored(uri: String): Boolean = uri.startsWith(SCHEME)
}
