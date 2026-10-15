package ca.stewartclan.andb.ui.design

import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.FieldType
import ca.stewartclan.andb.data.IdProps

/** What saving a field does to the values records already hold. */
enum class ChangeEffect {
    /** A new ID field: existing records get IDs. Informational, not a warning. */
    ID_BACKFILL,

    /** Values are replaced with new IDs. */
    ID_REPLACE,

    /** IDs are regenerated in the new kind. */
    ID_REGENERATE,

    /** Records with several choices keep only the first. */
    KEEP_FIRST_CHOICE,

    /** Values are converted to the new type, or removed. */
    CONVERT
}

/** The effect on existing values of saving [new] over [old] (null when adding a field), if any. */
fun changeEffect(old: FieldDef?, new: FieldDef): ChangeEffect? = when {
    new.type == FieldType.ID -> idChangeEffect(old, new)
    old == null -> null
    old.isMultiList && new.type == FieldType.LIST && !new.isMultiList -> ChangeEffect.KEEP_FIRST_CHOICE
    !old.keepsValuesAs(new) -> ChangeEffect.CONVERT
    else -> null
}

private fun idChangeEffect(old: FieldDef?, new: FieldDef): ChangeEffect? = when {
    old == null -> ChangeEffect.ID_BACKFILL
    old.type != FieldType.ID -> if (old.hasValues) ChangeEffect.ID_REPLACE else ChangeEffect.ID_BACKFILL
    (old.props as IdProps).kind != (new.props as IdProps).kind -> ChangeEffect.ID_REGENERATE
    else -> null
}
