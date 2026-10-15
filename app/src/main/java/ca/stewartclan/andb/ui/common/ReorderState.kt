package ca.stewartclan.andb.ui.common

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Drag-to-reorder for a keyed LazyColumn. The caller owns the item order: it moves the dragged item
 * when [drag] returns a new index, and saves the order when the drag ends.
 *
 * The dragged item is drawn at where the finger put it, independent of where the list lays it out,
 * so it never jumps while the list catches up with a move.
 */
@Stable
class ReorderState(private val listState: LazyListState) {
    var draggedKey by mutableStateOf<Any?>(null)
        private set

    /** Layout offset of the dragged item when the drag started. */
    private var startOffset = 0
    private var totalDrag by mutableFloatStateOf(0f)

    fun start(key: Any) {
        val item = listState.layoutInfo.visibleItemsInfo.find { it.key == key } ?: return
        draggedKey = key
        startOffset = item.offset
        totalDrag = 0f
    }

    /**
     * Follows the finger by [dy] pixels. Returns the list index the dragged item should move to
     * once its centre is over another item, or null to stay put.
     */
    fun drag(dy: Float): Int? {
        totalDrag += dy
        val visible = listState.layoutInfo.visibleItemsInfo
        val current = visible.find { it.key == draggedKey } ?: return null
        val centre = startOffset + totalDrag + current.size / 2f
        // Layout indexes, not keys: until the list re-lays out after a move, the item just passed
        // still sits at its old index, so asking to move there again changes nothing.
        return visible.find { it.key != draggedKey && centre >= it.offset && centre < it.offset + it.size }?.index
    }

    /** How far to shift item [key] from its layout position to draw it under the finger. */
    fun translationOf(key: Any): Float = listState.layoutInfo.visibleItemsInfo
        .find { it.key == key && key == draggedKey }
        ?.let { startOffset + totalDrag - it.offset }
        ?: 0f

    fun end() {
        draggedKey = null
        totalDrag = 0f
    }
}

/**
 * Makes this element (a drag handle) drag item [key] of [state]'s list: [onMoveTo] gets each new
 * index from [ReorderState.drag], then [onDrop] or [onCancel] when the finger lifts or is cancelled.
 */
fun Modifier.dragToReorder(
    state: ReorderState,
    key: Any,
    onMoveTo: (Int) -> Unit,
    onDrop: () -> Unit,
    onCancel: () -> Unit
): Modifier = pointerInput(key) {
    detectDragGestures(
        onDragStart = { state.start(key) },
        onDrag = { change, amount ->
            change.consume()
            state.drag(amount.y)?.let(onMoveTo)
        },
        onDragEnd = {
            state.end()
            onDrop()
        },
        onDragCancel = {
            state.end()
            onCancel()
        }
    )
}
