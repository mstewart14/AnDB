package ca.stewartclan.andb.ui.list

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.FieldDef

/** The search box that replaces the list's title while finding. */
@Composable
fun FindField(query: FindQuery, onChange: (FindQuery) -> Unit) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    TextField(
        value = query.text,
        onValueChange = { onChange(query.copy(text = it)) },
        placeholder = { Text(stringResource(R.string.find_placeholder)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus)
    )
}

/** Where and how to search, and how many records match. */
@Composable
fun FindOptions(
    query: FindQuery,
    searchable: List<FieldDef>,
    matchCount: Int,
    totalCount: Int,
    onChange: (FindQuery) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        val allFields = stringResource(R.string.find_all_fields)
        MenuButton(
            searchable.find { it.id == query.fieldId }?.name ?: allFields,
            listOf<Pair<Long?, String>>(null to allFields) + searchable.map { it.id to it.name },
            showAddIcon = false
        ) { onChange(query.copy(fieldId = it)) }
        FilterChip(
            selected = query.caseSensitive,
            onClick = { onChange(query.copy(caseSensitive = !query.caseSensitive)) },
            label = { Text(stringResource(R.string.view_sort_case_sensitive)) }
        )
        FilterChip(
            selected = query.wholeWord,
            onClick = { onChange(query.copy(wholeWord = !query.wholeWord)) },
            label = { Text(stringResource(R.string.find_whole_word)) }
        )
        Text(
            stringResource(R.string.find_count, matchCount, totalCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
