package ca.stewartclan.andb.data

/**
 * The value of a Link field: [uri] of the linked item, plus a snapshot kept in the database so the
 * record still shows something if the item goes away: its [name] and, for photos, a small JPEG
 * [thumbnail].
 */
class LinkValue(val uri: String, val name: String, val thumbnail: ByteArray?) {
    override fun equals(other: Any?): Boolean = other is LinkValue &&
        other.uri == uri &&
        other.name == name &&
        other.thumbnail.contentEquals(thumbnail)

    override fun hashCode(): Int = 31 * (31 * uri.hashCode() + name.hashCode()) + thumbnail.contentHashCode()

    override fun toString(): String = name
}

/**
 * What pointing a Link field to records at another database carries over: [links] matched in the
 * new database, by record id (records not in it keep their old link, shown as unavailable; null
 * clears every link instead), and the new target field of each Linked field showing through it,
 * by Linked field id (null for the new database's display field).
 */
data class Repoint(val links: Map<Long, LinkValue>?, val linkedTargets: Map<Long, Long?>)
