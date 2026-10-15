package ca.stewartclan.andb.ui.list

import kotlinx.serialization.Serializable

/**
 * What to find in records: [text] in field [fieldId], or in any field when null. Matches the text
 * as shown (formatted dates, IDs with their prefix, list choices).
 */
@Serializable
data class FindQuery(
    val text: String = "",
    val fieldId: Long? = null,
    val caseSensitive: Boolean = false,
    val wholeWord: Boolean = false
) {
    /** Nothing to look for yet: every record matches. */
    val isBlank: Boolean get() = text.isBlank()

    /**
     * A test for whether a record whose searchable fields show the given texts (by field id)
     * matches. Built once per search, then applied to each record.
     */
    fun matcher(): (Map<Long, String>) -> Boolean {
        val needle = text.trim()
        if (needle.isEmpty()) return { true }
        // A whole word has no letter or digit right before or after it.
        val body = Regex.escape(needle)
        val source = if (wholeWord) "(?<![\\p{L}\\p{N}])$body(?![\\p{L}\\p{N}])" else body
        val regex = Regex(source, if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
        return { texts ->
            val searched = if (fieldId == null) texts.values else listOfNotNull(texts[fieldId])
            searched.any { regex.containsMatchIn(it) }
        }
    }

    fun matches(texts: Map<Long, String>): Boolean = matcher()(texts)
}
