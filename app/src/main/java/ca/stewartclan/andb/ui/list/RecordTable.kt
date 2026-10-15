package ca.stewartclan.andb.ui.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.stewartclan.andb.data.ColumnWidth
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.SortKey
import ca.stewartclan.andb.data.canSort
import ca.stewartclan.andb.ui.common.rememberThumbnail

/**
 * Records as a table: a header row of field names (tap to sort) above the rows. Scrolls sideways
 * when the columns are wider than the screen; otherwise the last column takes up the rest.
 */
@Composable
fun RecordTable(
    columns: List<ListColumn>,
    rows: List<RecordRow>,
    sort: List<SortKey>,
    onSort: (FieldDef) -> Unit,
    onOpen: (Long) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.padding(top = contentPadding.calculateTopPadding())) {
        val natural = columns.map { it.width.dp }
        val spare = (maxWidth - natural.fold(0.dp) { sum, w -> sum + w }).coerceAtLeast(0.dp)
        val widths = natural.mapIndexed { i, w -> if (i == natural.lastIndex) w + spare else w }
        val tableWidth = widths.fold(0.dp) { sum, w -> sum + w }
        Column(
            Modifier
                .fillMaxHeight()
                .horizontalScroll(rememberScrollState())
        ) {
            Row(Modifier.width(tableWidth)) {
                columns.forEachIndexed { i, column ->
                    HeaderCell(column.field, sort, widths[i], onSort)
                }
            }
            HorizontalDivider(Modifier.width(tableWidth), thickness = 2.dp)
            LazyColumn(
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + FAB_CLEARANCE),
                modifier = Modifier.width(tableWidth)
            ) {
                items(rows, key = { it.id }) { row ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onOpen(row.id) }
                    ) {
                        row.cells.forEachIndexed { i, cell -> Cell(cell, widths[i]) }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(field: FieldDef, sort: List<SortKey>, width: Dp, onSort: (FieldDef) -> Unit) {
    val index = sort.indexOfFirst { it.fieldId == field.id }
    val marker = when {
        index < 0 -> ""
        // With several sort keys, number them so the order of significance shows.
        sort.size > 1 -> " ${if (sort[index].descending) "▼" else "▲"}${index + 1}"
        else -> " ${if (sort[index].descending) "▼" else "▲"}"
    }
    Text(
        field.name + marker,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .width(width)
            .clickable(enabled = field.canSort) { onSort(field) }
            .padding(horizontal = CELL_PADDING, vertical = 12.dp)
    )
}

@Composable
private fun Cell(cell: ListCell, width: Dp) {
    val thumbnail = rememberThumbnail(cell.thumbnail)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(width)
            .height(ROW_HEIGHT)
            .padding(horizontal = CELL_PADDING)
    ) {
        if (thumbnail != null) {
            Image(
                thumbnail,
                contentDescription = cell.text,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(THUMBNAIL_SIZE)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
        Text(
            cell.text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = if (thumbnail != null) 8.dp else 0.dp)
        )
    }
}

val ColumnWidth.dp: Dp
    get() = when (this) {
        ColumnWidth.NARROW -> 88.dp
        ColumnWidth.MEDIUM -> 144.dp
        ColumnWidth.WIDE -> 232.dp
    }

private val CELL_PADDING = 12.dp

/** Every row is this tall, with or without thumbnails, so rows line up. */
private val ROW_HEIGHT = 52.dp
private val THUMBNAIL_SIZE = 40.dp

/** Room under the last row so the + button doesn't cover it. */
private val FAB_CLEARANCE = 88.dp
