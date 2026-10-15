package ca.stewartclan.andb.ui.design

import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.FieldProps
import ca.stewartclan.andb.data.FieldType
import ca.stewartclan.andb.data.IdKind
import ca.stewartclan.andb.data.IdProps
import ca.stewartclan.andb.data.ListProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChangeEffectTest {
    private fun field(type: FieldType, props: FieldProps = FieldProps.default(type)) = FieldDef(1, 0, "F", type, props)

    @Test
    fun ids() {
        assertEquals(ChangeEffect.ID_BACKFILL, changeEffect(null, field(FieldType.ID)))
        assertEquals(ChangeEffect.ID_BACKFILL, changeEffect(field(FieldType.SECTION), field(FieldType.ID)))
        assertEquals(ChangeEffect.ID_REPLACE, changeEffect(field(FieldType.STRING), field(FieldType.ID)))
        assertEquals(
            ChangeEffect.ID_REGENERATE,
            changeEffect(field(FieldType.ID), field(FieldType.ID, IdProps(IdKind.SHORT_CODE)))
        )
        assertNull(changeEffect(field(FieldType.ID), field(FieldType.ID, IdProps(start = 9, prefix = "X"))))
    }

    @Test
    fun lists() {
        val single = field(FieldType.LIST, ListProps(listOf("a")))
        val multi = field(FieldType.LIST, ListProps(listOf("a"), multiple = true))
        assertNull(changeEffect(single, multi))
        assertEquals(ChangeEffect.KEEP_FIRST_CHOICE, changeEffect(multi, single))
        assertEquals(ChangeEffect.CONVERT, changeEffect(multi, field(FieldType.STRING)))
    }

    @Test
    fun otherTypes() {
        assertNull(changeEffect(null, field(FieldType.STRING)))
        assertNull(changeEffect(field(FieldType.STRING), field(FieldType.NOTE)))
        assertEquals(ChangeEffect.CONVERT, changeEffect(field(FieldType.STRING), field(FieldType.INTEGER)))
    }
}
