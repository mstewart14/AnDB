package ca.stewartclan.andb.ui.design

import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.FieldType
import ca.stewartclan.andb.data.LinkProps
import ca.stewartclan.andb.data.LinkSource
import ca.stewartclan.andb.data.LinkValue
import ca.stewartclan.andb.data.LinkedProps
import ca.stewartclan.andb.data.RecordRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelinkTest {
    private val newDb = "22222222-2222-2222-2222-222222222222"
    private val newTitles = mapOf(10L to "Lemon", 11L to "Lime", 12L to "Butter", 13L to "Butter")

    @Test
    fun `a saved title found once is relinked, ignoring case and spaces`() {
        val plan = Relink.plan(mapOf(1L to "lemon ", 2L to "Lime"), newTitles, newDb)
        assertEquals(
            mapOf(
                1L to LinkValue(RecordRef(newDb, 10).uri, "Lemon", null),
                2L to LinkValue(RecordRef(newDb, 11).uri, "Lime", null)
            ),
            plan.links
        )
        assertEquals(0, plan.notFound)
        assertEquals(0, plan.ambiguous)
    }

    @Test
    fun `a title found nowhere or more than once is left alone`() {
        val plan = Relink.plan(mapOf(1L to "Black Cardamon", 2L to "Butter", 3L to "Lime"), newTitles, newDb)
        assertEquals(setOf(3L), plan.links.keys)
        assertEquals(1, plan.notFound)
        assertEquals(1, plan.ambiguous)
    }

    @Test
    fun `Linked fields move to the field of the same name`() {
        val link = FieldDef(1, 0, "Aroma", FieldType.LINK, LinkProps(LinkSource.RECORD, "old"))
        val category = FieldDef(2, 1, "Its category", FieldType.LINKED, LinkedProps(1, targetFieldId = 7))
        val shown = FieldDef(3, 2, "Its title", FieldType.LINKED, LinkedProps(1, targetFieldId = null))
        val other = FieldDef(4, 3, "Elsewhere", FieldType.LINKED, LinkedProps(99, targetFieldId = 7))
        val oldTargets = listOf(FieldDef(7, 0, "Category", FieldType.STRING))
        val newTargets = listOf(FieldDef(1, 0, "Aroma", FieldType.STRING), FieldDef(2, 1, "category", FieldType.STRING))
        val fields = listOf(link, category, shown, other)

        assertEquals(mapOf(2L to 2L), Relink.linkedTargets(fields, 1, oldTargets, newTargets))
        // Old database gone, or no field of that name: the display field.
        assertEquals(mapOf(2L to null), Relink.linkedTargets(fields, 1, null, newTargets))
        assertEquals(mapOf(2L to null), Relink.linkedTargets(fields, 1, oldTargets, newTargets.take(1)))
    }

    @Test
    fun `pointing a record Link at another database is a repoint`() {
        val toOld = FieldDef(1, 0, "L", FieldType.LINK, LinkProps(LinkSource.RECORD, "old"))
        val toNew = toOld.copy(props = LinkProps(LinkSource.RECORD, newDb))
        assertTrue(isRepoint(toOld, toNew))
        assertEquals(ChangeEffect.RELINK, changeEffect(toOld, toNew))
        assertFalse(isRepoint(toOld, toOld.copy(props = LinkProps(LinkSource.RECORD, "old", displayFieldId = 3))))
        assertFalse(isRepoint(toOld, toOld.copy(props = LinkProps(LinkSource.PHOTO))))
        assertNull(changeEffect(toOld, toOld.copy(name = "M")))
    }

    @Test
    fun `a link points at its field's database only`() {
        val field = FieldDef(1, 0, "L", FieldType.LINK, LinkProps(LinkSource.RECORD, newDb))
        assertEquals(5L, field.linkedRecordId(LinkValue(RecordRef(newDb, 5).uri, "x", null)))
        assertNull(field.linkedRecordId(LinkValue(RecordRef("old", 5).uri, "x", null)))
        assertNull(field.linkedRecordId(null))
    }
}
