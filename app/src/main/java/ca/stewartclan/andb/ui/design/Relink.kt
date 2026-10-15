package ca.stewartclan.andb.ui.design

import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.LinkValue
import ca.stewartclan.andb.data.LinkedProps
import ca.stewartclan.andb.data.RecordRef
import ca.stewartclan.andb.ui.common.RecordTitles

/**
 * Links carried over to another database by title: [links] found there, by record id; and how
 * many weren't, because no record ([notFound]) or several ([ambiguous]) have the title.
 */
data class RelinkPlan(val links: Map<Long, LinkValue>, val notFound: Int, val ambiguous: Int)

/** Carrying a Link field's links and Linked fields over when it's pointed at another database. */
object Relink {
    /**
     * Matches each link's saved title in [saved] (by record id) to the one record of database
     * [databaseId] with that title in [titles], ignoring case.
     */
    fun plan(saved: Map<Long, String>, titles: Map<Long, String>, databaseId: String): RelinkPlan {
        val matches = saved.mapValues { (_, title) -> RecordTitles.matching(title, titles) }
        return RelinkPlan(
            links = matches.filterValues { it.size == 1 }.mapValues { (_, ids) ->
                val target = ids.single()
                LinkValue(RecordRef(databaseId, target).uri, titles.getValue(target), null)
            },
            notFound = matches.count { it.value.isEmpty() },
            ambiguous = matches.count { it.value.size > 1 }
        )
    }

    /**
     * The new target of each Linked field among [fields] that shows a chosen field through Link
     * field [linkFieldId]: the field of the same name in [newTargets], or null (the display field)
     * if there is none or the old one's name is unknown because [oldTargets] (null) is gone.
     */
    fun linkedTargets(
        fields: List<FieldDef>,
        linkFieldId: Long,
        oldTargets: List<FieldDef>?,
        newTargets: List<FieldDef>
    ): Map<Long, Long?> = fields.mapNotNull { field ->
        val props = field.props as? LinkedProps ?: return@mapNotNull null
        if (props.linkFieldId != linkFieldId || props.targetFieldId == null) return@mapNotNull null
        val name = oldTargets?.find { it.id == props.targetFieldId }?.name
        field.id to name?.let { n -> newTargets.find { it.name.equals(n, ignoreCase = true) }?.id }
    }.toMap()
}
