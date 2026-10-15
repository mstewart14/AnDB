package ca.stewartclan.andb.data

/** How a field's values are stored in its record-table column. */
enum class Storage(val sqlType: String?) {
    TEXT("TEXT"),
    INTEGER("INTEGER"),
    REAL("REAL"),

    /** The field has no column: it is computed or purely presentational. */
    NONE(null)
}

/**
 * Field types (see docs/design.md). [code] is what is written to the field table, so it must never
 * change once released; the enum names and order are free to change.
 */
enum class FieldType(val code: String, val storage: Storage) {
    STRING("string", Storage.TEXT),
    NOTE("note", Storage.TEXT),
    BOOLEAN("boolean", Storage.INTEGER),
    INTEGER("integer", Storage.INTEGER),
    FLOAT("float", Storage.REAL),
    DATE("date", Storage.TEXT),
    TIME("time", Storage.TEXT),
    LIST("list", Storage.TEXT),
    LINK("link", Storage.TEXT),
    LINKED("linked", Storage.NONE),
    EMAIL("email", Storage.TEXT),
    PHONE("phone", Storage.TEXT),
    URL("url", Storage.TEXT),
    RATING("rating", Storage.INTEGER),
    SCALE("scale", Storage.INTEGER),
    SECTION("section", Storage.NONE),
    CREATED("created", Storage.NONE),
    MODIFIED("modified", Storage.NONE);

    companion object {
        private val byCode = entries.associateBy { it.code }

        fun fromCode(code: String): FieldType =
            byCode[code] ?: throw IllegalArgumentException("Unknown field type code: $code")
    }
}
