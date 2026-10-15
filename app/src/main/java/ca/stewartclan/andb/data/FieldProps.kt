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
            else -> NoProps
        }

        fun decode(type: FieldType, text: String): FieldProps = when (type) {
            FieldType.ID -> json.decodeFromString(IdProps.serializer(), text)
            else -> NoProps
        }

        fun encode(props: FieldProps): String = when (props) {
            is IdProps -> json.encodeToString(IdProps.serializer(), props)
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
