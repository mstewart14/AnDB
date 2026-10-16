package ca.stewartclan.andb.data

data class FieldDef(
    val id: Long,
    val position: Int,
    val name: String,
    val type: FieldType,
    val props: FieldProps = FieldProps.default(type),
    /** Records should not be left without a value here; see [FieldType.canBeRequired]. */
    val required: Boolean = false
) {
    /** A List allowing several choices keeps them in a side table instead of a record column. */
    val isMultiList: Boolean get() = (props as? ListProps)?.multiple == true

    /** A Link to records allowing several keeps them in a side table instead of a record column. */
    val isMultiLink: Boolean get() = recordLink?.multiple == true

    /** How this field's values sit in the record table; [Storage.NONE] if not there. */
    val storage: Storage get() = if (isMultiList || isMultiLink) Storage.NONE else type.storage

    /** Whether this Link field and [other] link to the same kind of thing (and database, for records). */
    fun linksTheSameWay(other: FieldDef): Boolean {
        val mine = props as? LinkProps
        val theirs = other.props as? LinkProps
        return mine?.source == theirs?.source && mine?.databaseId == theirs?.databaseId
    }

    /** For a Link field to records, which database and display field; null for other fields. */
    val recordLink: LinkProps? get() = (props as? LinkProps)?.takeIf { it.source == LinkSource.RECORD }

    /** Whether this is a Link field to photos, whose cells show a thumbnail. */
    val isPhotoLink: Boolean get() = (props as? LinkProps)?.source == LinkSource.PHOTO

    /**
     * The id of the record this Link field's [link] points at, if it points into the field's
     * database; null for a link left over from a database the field linked to before.
     */
    fun linkedRecordId(link: LinkValue?): Long? = link?.let { RecordRef.parse(it.uri) }
        ?.takeIf { it.databaseId == recordLink?.databaseId }?.recordId

    /** The records this Link field's stored [value] (one link or several) points at, as [linkedRecordId]. */
    fun linkedRecordIds(value: Any?): List<Long> = linkValues(value).mapNotNull { linkedRecordId(it) }

    /** Whether values are text (or may be, as IDs), so text matching and case apply. */
    val isTextual: Boolean get() = isMultiList || isMultiLink || storage == Storage.TEXT || storage == Storage.ANY

    /** Whether the field holds values at all, in a record column or a side table. */
    val hasValues: Boolean get() = storage != Storage.NONE || isMultiList || isMultiLink

    /**
     * Whether changing this field into [new] keeps its values as they are. Becoming an ID replaces
     * them with generated ones; multiple choices narrowed to one keep only the first.
     */
    fun keepsValuesAs(new: FieldDef): Boolean = when {
        isMultiList -> new.isMultiList
        !hasValues -> true
        new.type == FieldType.ID -> type == FieldType.ID
        // Other values aren't links, links (uris) mean nothing as other values, and links to one
        // kind of target or database mean nothing for another.
        new.type == FieldType.LINK || type == FieldType.LINK -> type == new.type && linksTheSameWay(new)
        new.isMultiList -> true
        else -> storage == new.storage
    }
}
