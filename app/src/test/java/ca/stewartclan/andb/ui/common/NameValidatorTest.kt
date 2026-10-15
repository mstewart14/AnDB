package ca.stewartclan.andb.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class NameValidatorTest {
    private val existing = listOf("Books", "Wine cellar")

    @Test
    fun acceptsNewName() {
        assertEquals(NameProblem.OK, NameValidator.check("Recipes", existing))
    }

    @Test
    fun rejectsBlank() {
        assertEquals(NameProblem.BLANK, NameValidator.check("   ", existing))
    }

    @Test
    fun rejectsTooLong() {
        val name = "x".repeat(NameValidator.MAX_LENGTH + 1)
        assertEquals(NameProblem.TOO_LONG, NameValidator.check(name, existing))
    }

    @Test
    fun rejectsDuplicateIgnoringCaseAndSpaces() {
        assertEquals(NameProblem.DUPLICATE, NameValidator.check("  books ", existing))
    }

    @Test
    fun renameMayKeepOrRecaseOwnName() {
        assertEquals(NameProblem.OK, NameValidator.check("BOOKS", existing, ownName = "Books"))
    }

    @Test
    fun renameMayNotTakeAnotherName() {
        assertEquals(NameProblem.DUPLICATE, NameValidator.check("wine cellar", existing, ownName = "Books"))
    }
}
