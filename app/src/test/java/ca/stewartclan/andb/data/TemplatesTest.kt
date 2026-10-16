package ca.stewartclan.andb.data

import ca.stewartclan.andb.ui.common.NameProblem
import ca.stewartclan.andb.ui.common.NameValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplatesTest {
    @Test
    fun idsAndNamesAreUnique() {
        assertEquals(Templates.all.size, Templates.all.map { it.id }.toSet().size)
        assertEquals(Templates.all.size, Templates.all.map { it.name }.toSet().size)
    }

    @Test
    fun everyFieldNameIsOneTheDesignViewWouldAccept() {
        Templates.all.forEach { template ->
            template.fields.forEachIndexed { i, field ->
                val earlier = template.fields.take(i).map { it.name }
                assertEquals(
                    "${template.name}: ${field.name}",
                    NameProblem.OK,
                    NameValidator.check(field.name, earlier)
                )
            }
        }
    }

    @Test
    fun listsHaveDistinctItemsAndRequiredFieldsCanBeRequired() {
        Templates.all.flatMap { it.fields }.forEach { field ->
            (field.props as? ListProps)?.let {
                assertEquals(it.items.size, it.items.map(String::lowercase).toSet().size)
            }
            if (field.required) assertTrue(field.name, field.type.canBeRequired)
        }
    }

    @Test
    fun findByIdAndUnknownIds() {
        assertEquals("Books", Templates.find("books")?.name)
        assertEquals(null, Templates.find("nope"))
    }
}
