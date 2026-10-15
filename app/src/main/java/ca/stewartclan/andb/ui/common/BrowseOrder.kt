package ca.stewartclan.andb.ui.common

import java.util.concurrent.ConcurrentHashMap

/**
 * The order of records the list view last showed, per database, so the record view's previous and
 * next follow the list. Lives as long as the app process; without it, records go oldest first.
 */
class BrowseOrder {
    private val orders = ConcurrentHashMap<String, List<Long>>()

    operator fun set(databaseId: String, recordIds: List<Long>) {
        orders[databaseId] = recordIds
    }

    /**
     * [stored] (all record ids of the database) in the list's order. Records the list didn't show,
     * such as ones added since, come after, oldest first.
     */
    fun arrange(databaseId: String, stored: List<Long>): List<Long> {
        val order = orders[databaseId] ?: return stored
        val present = stored.toSet()
        val listed = order.filter { it in present }
        val listedSet = listed.toSet()
        return listed + stored.filter { it !in listedSet }
    }
}
