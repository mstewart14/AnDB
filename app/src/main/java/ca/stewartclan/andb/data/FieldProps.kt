package ca.stewartclan.andb.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Per-type field settings, stored as JSON in `andb_field.props`. */
sealed interface FieldProps {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

        /** Settings a new field of [type] starts with. */
        fun default(type: FieldType): FieldProps = when (type) {
            FieldType.ID -> IdProps()
            FieldType.LIST -> ListProps()
            FieldType.LINK -> LinkProps()
            else -> NoProps
        }

        fun decode(type: FieldType, text: String): FieldProps = when (type) {
            FieldType.ID -> json.decodeFromString(IdProps.serializer(), text)
            FieldType.LIST -> json.decodeFromString(ListProps.serializer(), text)
            FieldType.LINK -> json.decodeFromString(LinkProps.serializer(), text)
            else -> NoProps
        }

        fun encode(props: FieldProps): String = when (props) {
            is IdProps -> json.encodeToString(IdProps.serializer(), props)
            is ListProps -> json.encodeToString(ListProps.serializer(), props)
            is LinkProps -> json.encodeToString(LinkProps.serializer(), props)
            NoProps -> "{}"
        }
    }
}

/** For types that have no settings (yet). */
data object NoProps : FieldProps

@Serializable
enum class IdKind {
    @SerialName("increment")
    INCREMENT,

    @SerialName("uuid4")
    UUID_RANDOM,

    @SerialName("uuid7")
    UUID_TIME,

    @SerialName("code")
    SHORT_CODE
}

/**
 * Settings of an ID field. [start], [prefix] and [padding] only apply to [IdKind.INCREMENT];
 * prefix and padding are display only, the stored value is the plain number.
 */
@Serializable
data class IdProps(
    val kind: IdKind = IdKind.INCREMENT,
    val start: Long = 1,
    val prefix: String = "",
    val padding: Int = 0
) : FieldProps {
    /** How an increment value is shown, e.g. `INV-0042` for prefix `INV-` and padding 4. */
    fun format(value: Long): String = prefix + value.toString().padStart(padding, '0')

    companion object {
        const val MAX_PREFIX_LENGTH = 16
        const val MAX_PADDING = 12
    }
}

/**
 * Settings of a List field: choices come from [items], or from [lookup] when set. Records store the
 * chosen text itself (a snapshot), so editing the choices later never changes existing records.
 */
@Serializable
data class ListProps(val items: List<String> = emptyList(), val multiple: Boolean = false, val lookup: Lookup? = null) :
    FieldProps

/** Choices taken from the current values of field [fieldId] in database [databaseId]. */
@Serializable
data class Lookup(val databaseId: String, val fieldId: Long)

/** What a Link field points at. More sources (records, contacts, files) are planned. */
@Serializable
enum class LinkSource {
    @SerialName("photo")
    PHOTO
}

@Serializable
data class LinkProps(val source: LinkSource = LinkSource.PHOTO) : FieldProps
