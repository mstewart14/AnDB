package ca.stewartclan.andb.ui.chooser

/** Why a proposed database name can't be used, or [OK]. */
enum class NameProblem { OK, BLANK, TOO_LONG, DUPLICATE }

object DatabaseNameValidator {
    const val MAX_LENGTH = 64

    /**
     * Names are compared trimmed and case-insensitively, like Pilot-DB's chooser. [ownName] is the
     * database being renamed, so keeping (or re-casing) its current name is allowed.
     */
    fun check(name: String, existingNames: Collection<String>, ownName: String? = null): NameProblem {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> NameProblem.BLANK
            trimmed.length > MAX_LENGTH -> NameProblem.TOO_LONG
            existingNames.any { it.equals(trimmed, ignoreCase = true) && !it.equals(ownName, ignoreCase = true) } ->
                NameProblem.DUPLICATE
            else -> NameProblem.OK
        }
    }
}
