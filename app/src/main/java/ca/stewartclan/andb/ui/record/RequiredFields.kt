package ca.stewartclan.andb.ui.record

import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.FieldType
import ca.stewartclan.andb.data.RequiredRule

/** Rules for required fields and empty records, over the record view's editor state. */
object RequiredFields {
    /** Required fields that have no value yet, in field order. */
    fun missing(fields: List<FieldDef>, drafts: Map<Long, String>, chosen: Map<Long, List<String>>): List<FieldDef> =
        RequiredRule.missing(fields, values(fields, drafts), chosen)

    /** The editor [drafts] as they would be stored. */
    fun values(fields: List<FieldDef>, drafts: Map<Long, String>): Map<Long, Any?> =
        fields.filter { it.id in drafts }.associate { it.id to FieldValues.fromDraft(it, drafts.getValue(it.id)) }

    /**
     * Whether nothing has been entered at all, so a new record can be dropped without asking. Not
     * a required-field check: a ticked checkbox counts as something entered, though checkboxes
     * can't be required.
     */
    fun isBlank(fields: List<FieldDef>, drafts: Map<Long, String>, chosen: Map<Long, List<String>>): Boolean =
        fields.filter { it.type.canBeRequired || it.type == FieldType.BOOLEAN }.none { hasValue(it, drafts, chosen) }

    /** Like [RequiredRule.hasValue], but an unticked checkbox (stored as 0) counts as nothing entered. */
    private fun hasValue(field: FieldDef, drafts: Map<Long, String>, chosen: Map<Long, List<String>>): Boolean {
        val draft = drafts[field.id]
        return if (field.type == FieldType.BOOLEAN) {
            draft == "1"
        } else {
            RequiredRule.hasValue(field, draft?.let { FieldValues.fromDraft(field, it) }, chosen[field.id].orEmpty())
        }
    }
}
