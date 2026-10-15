package ca.stewartclan.andb.ui.design

import androidx.compose.runtime.saveable.Saver
import ca.stewartclan.andb.data.ListProps
import ca.stewartclan.andb.data.Lookup
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** List settings as edited, before validation. Both sources are kept so switching back loses nothing. */
@Serializable
data class ListDraft(
    val items: List<String> = listOf(""),
    val multiple: Boolean = false,
    val useLookup: Boolean = false,
    val lookupDatabaseId: String? = null,
    val lookupFieldId: Long? = null
) {
    /** The items to save: all but a blank last one, which is a new row not typed into yet. */
    private val enteredItems: List<String>
        get() = if (items.lastOrNull()?.isBlank() == true) items.dropLast(1) else items

    /** Indexes of entered items that are blank or repeat an earlier item (ignoring case and spaces). */
    val badItems: Set<Int>
        get() {
            val seen = mutableSetOf<String>()
            return enteredItems.indices.filter { i ->
                val key = enteredItems[i].trim().lowercase()
                key.isEmpty() || !seen.add(key)
            }.toSet()
        }

    /** The settings, or null while they're incomplete. */
    fun toProps(): ListProps? = when {
        useLookup && lookupDatabaseId != null && lookupFieldId != null ->
            ListProps(multiple = multiple, lookup = Lookup(lookupDatabaseId, lookupFieldId))
        useLookup -> null
        enteredItems.isEmpty() || badItems.isNotEmpty() -> null
        else -> ListProps(items = enteredItems.map { it.trim() }, multiple = multiple)
    }

    fun withItem(index: Int, text: String) = copy(items = items.toMutableList().apply { set(index, text) })

    fun withoutItem(index: Int) = copy(items = items.toMutableList().apply { removeAt(index) })

    fun withItemMoved(from: Int, to: Int) = copy(items = items.toMutableList().apply { add(to, removeAt(from)) })

    companion object {
        fun of(props: ListProps) = ListDraft(
            items = props.items.ifEmpty { listOf("") },
            multiple = props.multiple,
            useLookup = props.lookup != null,
            lookupDatabaseId = props.lookup?.databaseId,
            lookupFieldId = props.lookup?.fieldId
        )

        val Saver = Saver<ListDraft, String>(
            save = { Json.encodeToString(serializer(), it) },
            restore = { Json.decodeFromString(serializer(), it) }
        )
    }
}
