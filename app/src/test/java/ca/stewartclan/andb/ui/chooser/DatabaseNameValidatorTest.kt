package ca.stewartclan.andb.ui.chooser

import org.junit.Assert.assertEquals
import org.junit.Test

class DatabaseNameValidatorTest {
    private val existing = listOf("Books", "Wine cellar")

    @Test
    fun acceptsNewName() {
        assertEquals(NameProblem.OK, DatabaseNameValidator.check("Recipes", existing))
    }

    @Test
    fun rejectsBlank() {
        assertEquals(NameProblem.BLANK, DatabaseNameValidator.check("   ", existing))
    }

    @Test
    fun rejectsTooLong() {
        val name = "x".repeat(DatabaseNameValidator.MAX_LENGTH + 1)
        assertEquals(NameProblem.TOO_LONG, DatabaseNameValidator.check(name, existing))
    }

    @Test
    fun rejectsDuplicateIgnoringCaseAndSpaces() {
        assertEquals(NameProblem.DUPLICATE, DatabaseNameValidator.check("  books ", existing))
    }

    @Test
    fun renameMayKeepOrRecaseOwnName() {
        assertEquals(NameProblem.OK, DatabaseNameValidator.check("BOOKS", existing, ownName = "Books"))
    }

    @Test
    fun renameMayNotTakeAnotherName() {
        assertEquals(NameProblem.DUPLICATE, DatabaseNameValidator.check("wine cellar", existing, ownName = "Books"))
    }
}
