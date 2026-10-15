package ca.stewartclan.andb.ui.design

import ca.stewartclan.andb.data.DatabaseRepository
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.LinkProps
import ca.stewartclan.andb.data.LinkSource
import ca.stewartclan.andb.data.Repoint
import ca.stewartclan.andb.ui.common.RecordTitles

/**
 * A database to point a Link field at instead of its deleted one: [databaseId], shown by
 * [displayFieldId], relinking as [plan] says. [byName] when it has the deleted database's name.
 */
data class Replacement(val databaseId: String, val displayFieldId: Long?, val plan: RelinkPlan, val byName: Boolean) {
    val link: LinkProps get() = LinkProps(LinkSource.RECORD, databaseId, displayFieldId)
}

/** A Link [field] repaired by name: now linking into [databaseName], with [plan]'s links carried over. */
data class Repaired(val field: FieldDef, val databaseName: String, val plan: RelinkPlan)

/** Finding and fixing Link fields whose database was deleted. */
class LinkRepair(private val repository: DatabaseRepository, private val titles: RecordTitles) {

    /** Link fields to records among [fields] whose database no longer exists. */
    suspend fun broken(fields: List<FieldDef>): List<FieldDef> {
        if (repository.databases.value == null) repository.refresh()
        val ids = repository.databases.value.orEmpty().map { it.id }.toSet()
        return fields.filter { field -> field.recordLink?.databaseId?.let { it !in ids } == true }
    }

    /**
     * The best replacement for broken Link [field] of database [id]: the one database with the
     * deleted database's name, if there is exactly one; otherwise the database whose records match
     * the most saved titles. Null if none matches any.
     */
    suspend fun suggest(id: String, field: FieldDef): Replacement? {
        val saved = repository.linkTitles(id, field.id)
        sameName(field)?.let { return bestMatch(saved, it).copy(byName = true) }
        return repository.databases.value.orEmpty().filter { it.id != id }
            .map { bestMatch(saved, it.id) }
            .filter { it.plan.links.isNotEmpty() }
            .maxByOrNull { it.plan.links.size }
    }

    /** The one database with the name of broken Link [field]'s deleted database, if exactly one. */
    private fun sameName(field: FieldDef): String? {
        val name = field.recordLink?.missingName?.trim() ?: return null
        return repository.databases.value.orEmpty().filter { it.name.trim().equals(name, ignoreCase = true) }
            .singleOrNull()?.id
    }

    /**
     * Repairs each broken Link field among [fields] of database [id] whose deleted database's name
     * now belongs to exactly one database: it links there instead, relinked by title.
     */
    suspend fun repairByName(id: String, fields: List<FieldDef>): List<Repaired> =
        broken(fields).filter { sameName(it) != null }.mapNotNull { field ->
            val replacement = suggest(id, field) ?: return@mapNotNull null
            val new = field.copy(props = replacement.link)
            repository.updateField(id, new, repoint(id, fields, field, new, relink = true))
            val name = repository.databases.value.orEmpty().find { it.id == replacement.databaseId }?.name
            Repaired(field, name.orEmpty(), replacement.plan)
        }

    /**
     * The field of database [databaseId] whose values match the most of [saved] titles, as a
     * replacement with that plan; the database's default display field if none matches.
     */
    suspend fun bestMatch(saved: Map<Long, String>, databaseId: String): Replacement {
        val best = titles.titlesByField(databaseId)
            .map { (field, byRecord) -> field.id to Relink.plan(saved, byRecord, databaseId) }
            .maxByOrNull { (_, plan) -> plan.links.size }
            ?.takeIf { (_, plan) -> plan.links.isNotEmpty() }
        return Replacement(
            databaseId,
            best?.first,
            best?.second ?: Relink.plan(saved, titles.titles(LinkProps(LinkSource.RECORD, databaseId)), databaseId),
            byName = false
        )
    }

    /** What relinking would do for Link [field] of database [id] pointed at [link]'s database. */
    suspend fun plan(id: String, field: FieldDef, link: LinkProps): RelinkPlan =
        Relink.plan(repository.linkTitles(id, field.id), titles.titles(link), link.databaseId.orEmpty())

    /**
     * What pointing Link [old] among [fields] of database [id] at [new]'s other database carries
     * over: links found there by title if [relink], and its Linked fields' targets by name.
     */
    suspend fun repoint(id: String, fields: List<FieldDef>, old: FieldDef, new: FieldDef, relink: Boolean): Repoint? {
        val to = new.recordLink?.takeIf { isRepoint(old, new) } ?: return null
        // The old database may be gone, and with it the names of the fields Linked fields showed.
        val oldTargets = runCatching { repository.fields(old.recordLink?.databaseId.orEmpty()) }.getOrNull()
        val newTargets = repository.fields(to.databaseId.orEmpty())
        return Repoint(
            links = if (relink) plan(id, old, to).links else null,
            linkedTargets = Relink.linkedTargets(fields, old.id, oldTargets, newTargets)
        )
    }
}
