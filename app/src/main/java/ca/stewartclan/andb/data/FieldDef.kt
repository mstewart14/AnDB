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

    /** How this field's values sit in the record table; [Storage.NONE] if not there. */
    val storage: Storage get() = if (isMultiList) Storage.NONE else type.storage

    /** Whether values are text (or may be, as IDs), so text matching and case apply. */
    val isTextual: Boolean get() = isMultiList || storage == Storage.TEXT || storage == Storage.ANY

    /** Whether the field holds values at all, in a record column or a side table. */
    val hasValues: Boolean get() = storage != Storage.NONE || isMultiList

    /**
     * Whether changing this field into [new] keeps its values as they are. Becoming an ID replaces
     * them with generated ones; multiple choices narrowed to one keep only the first.
     */
    fun keepsValuesAs(new: FieldDef): Boolean = when {
        isMultiList -> new.isMultiList
        !hasValues -> true
        new.type == FieldType.ID -> type == FieldType.ID
        // Other values aren't links, and links (uris) mean nothing as other values.
        new.type == FieldType.LINK || type == FieldType.LINK -> type == new.type
        new.isMultiList -> true
        else -> storage == new.storage
    }
}
