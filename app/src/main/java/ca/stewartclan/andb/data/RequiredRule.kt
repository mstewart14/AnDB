package ca.stewartclan.andb.data

/** When a required field counts as filled in. Shared by storage, which enforces it, and the record view. */
object RequiredRule {
    /** Required [fields] without a value in [values] (stored form) or, for multiple-choice Lists, [choices]. */
    fun missing(fields: List<FieldDef>, values: Map<Long, Any?>, choices: Map<Long, List<String>>): List<FieldDef> =
        fields.filter { it.required && !hasValue(it, values[it.id], choices[it.id].orEmpty()) }

    fun hasValue(field: FieldDef, value: Any?, choices: List<String>): Boolean = when {
        field.isMultiList -> choices.isNotEmpty()
        value == null -> false
        value is String -> value.isNotBlank()
        // A rating of 0 stars is how "no rating" is stored.
        field.type == FieldType.RATING -> value != 0L
        else -> true
    }
}
