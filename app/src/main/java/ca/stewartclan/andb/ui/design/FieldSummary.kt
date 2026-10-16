package ca.stewartclan.andb.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.IdProps
import ca.stewartclan.andb.data.LinkProps
import ca.stewartclan.andb.data.LinkSource
import ca.stewartclan.andb.data.LinkedProps
import ca.stewartclan.andb.data.ListProps
import ca.stewartclan.andb.data.NoProps

/** Names the field summaries need: databases by id, and each Linked field's target field. */
class SummaryNames(val databases: Map<String, String>, val linkedTargets: Map<Long, String>)

/** The type line under a field's name, e.g. "ID · Short code" or "List · 3 items". */
@Composable
fun fieldSummary(field: FieldDef, fields: List<FieldDef>, names: SummaryNames): String {
    val parts = mutableListOf(stringResource(field.type.labelRes))
    when (val props = field.props) {
        is IdProps -> parts += stringResource(props.kind.labelRes)
        is ListProps -> parts += listSummary(props, names.databases)
        is LinkProps -> {
            parts += linkSummary(props, names.databases)
            if (field.isMultiLink) parts += stringResource(R.string.link_summary_multiple)
        }
        is LinkedProps -> parts += linkedSummary(field, props, fields, names.linkedTargets)
        NoProps -> Unit
    }
    if (field.required) parts += stringResource(R.string.design_summary_required)
    return parts.joinToString(" · ")
}

@Composable
private fun listSummary(props: ListProps, databaseNames: Map<String, String>): List<String> {
    val source = when (val lookup = props.lookup) {
        null -> pluralStringResource(R.plurals.list_summary_items, props.items.size, props.items.size)
        else -> databaseNames[lookup.databaseId]?.let { stringResource(R.string.list_summary_lookup, it) }
            ?: stringResource(R.string.list_summary_lookup_missing)
    }
    return if (props.multiple) listOf(stringResource(R.string.list_summary_multiple), source) else listOf(source)
}

/** "Author › Country": the link a Linked field shows through, and the field it shows. */
@Composable
private fun linkedSummary(
    field: FieldDef,
    props: LinkedProps,
    fields: List<FieldDef>,
    targets: Map<Long, String>
): String {
    val link = fields.find { it.id == props.linkFieldId }?.name
    return if (link == null) {
        stringResource(R.string.list_summary_lookup_missing)
    } else {
        stringResource(R.string.field_linked_summary, link, targets[field.id] ?: "…")
    }
}

@Composable
private fun linkSummary(props: LinkProps, databaseNames: Map<String, String>): String = when (props.source) {
    LinkSource.PHOTO -> stringResource(R.string.link_source_photo)
    LinkSource.RECORD -> databaseNames[props.databaseId]?.let { stringResource(R.string.link_record_summary, it) }
        ?: stringResource(R.string.list_summary_lookup_missing)
    LinkSource.CONTACT -> stringResource(R.string.link_source_contact)
    LinkSource.FILE -> stringResource(R.string.link_source_file)
}
