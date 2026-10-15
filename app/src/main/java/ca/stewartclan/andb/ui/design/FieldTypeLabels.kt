package ca.stewartclan.andb.ui.design

import androidx.annotation.StringRes
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.FieldType

/** Types offered in the type picker. List, Link and Linked wait for the field properties editor. */
val designableTypes: List<FieldType> =
    FieldType.entries - setOf(FieldType.LIST, FieldType.LINK, FieldType.LINKED)

@get:StringRes
val FieldType.labelRes: Int
    get() = when (this) {
        FieldType.STRING -> R.string.field_type_string
        FieldType.NOTE -> R.string.field_type_note
        FieldType.BOOLEAN -> R.string.field_type_boolean
        FieldType.INTEGER -> R.string.field_type_integer
        FieldType.FLOAT -> R.string.field_type_float
        FieldType.DATE -> R.string.field_type_date
        FieldType.TIME -> R.string.field_type_time
        FieldType.LIST -> R.string.field_type_list
        FieldType.LINK -> R.string.field_type_link
        FieldType.LINKED -> R.string.field_type_linked
        FieldType.EMAIL -> R.string.field_type_email
        FieldType.PHONE -> R.string.field_type_phone
        FieldType.URL -> R.string.field_type_url
        FieldType.RATING -> R.string.field_type_rating
        FieldType.SCALE -> R.string.field_type_scale
        FieldType.SECTION -> R.string.field_type_section
        FieldType.CREATED -> R.string.field_type_created
        FieldType.MODIFIED -> R.string.field_type_modified
        FieldType.ID -> R.string.field_type_id
    }
