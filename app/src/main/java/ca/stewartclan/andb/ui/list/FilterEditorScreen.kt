package ca.stewartclan.andb.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.stewartclan.andb.R
import ca.stewartclan.andb.data.Condition
import ca.stewartclan.andb.data.FieldDef
import ca.stewartclan.andb.data.Op
import ca.stewartclan.andb.data.ValueKind
import ca.stewartclan.andb.data.valueKind
import ca.stewartclan.andb.ui.common.ErrorSnackbar
import ca.stewartclan.andb.ui.common.NameProblem
import ca.stewartclan.andb.ui.record.DateEditor
import ca.stewartclan.andb.ui.record.FieldValues
import ca.stewartclan.andb.ui.record.TimeEditor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterEditorScreen(
    onDone: () -> Unit,
    viewModel: FilterEditorViewModel = viewModel(factory = FilterEditorViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    ErrorSnackbar(error, snackbarHostState, viewModel::errorShown)
    LaunchedEffect(state?.done) { if (state?.done == true) onDone() }
    val isNew = state?.draft?.id == NEW_FILTER_ID

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isNew) R.string.filter_new else R.string.filter_edit)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_cancel)
                        )
                    }
                },
                actions = {
                    if (state != null && !isNew) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.filter_delete))
                        }
                    }
                    TextButton(onClick = viewModel::save, enabled = state?.canSave == true) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        state?.let { FilterEditorContent(it, viewModel, Modifier.padding(innerPadding)) }
    }
}

@Composable
private fun FilterEditorContent(state: FilterEditorState, viewModel: FilterEditorViewModel, modifier: Modifier) {
    val draft = state.draft
    LazyColumn(contentPadding = PaddingValues(16.dp), modifier = modifier.fillMaxSize()) {
        item {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { name -> viewModel.edit { it.copy(name = name) } },
                label = { Text(stringResource(R.string.name_label)) },
                singleLine = true,
                isError = state.nameProblem != NameProblem.OK,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                listOf(true to R.string.filter_match_all, false to R.string.filter_match_any).forEachIndexed {
                        i,
                        (all, label)
                    ->
                    SegmentedButton(
                        selected = draft.matchAll == all,
                        onClick = { viewModel.edit { it.copy(matchAll = all) } },
                        shape = SegmentedButtonDefaults.itemShape(i, 2)
                    ) { Text(stringResource(label)) }
                }
            }
        }
        item { SectionHeading(stringResource(R.string.filter_conditions)) }
        itemsIndexed(draft.conditions) { index, condition ->
            state.fieldsById[condition.fieldId]?.let { field ->
                ConditionRow(
                    condition = condition,
                    field = field,
                    fields = state.fields,
                    choices = state.listChoices[field.id].orEmpty(),
                    onChange = { viewModel.setCondition(index, it) },
                    onRemove = { viewModel.removeCondition(index) }
                )
            }
        }
        item {
            if (state.fields.isEmpty()) {
                Text(stringResource(R.string.filter_no_fields), style = MaterialTheme.typography.bodyMedium)
            } else {
                TextButton(onClick = viewModel::addCondition) { Text(stringResource(R.string.filter_add_condition)) }
            }
        }
    }
}

@Composable
private fun ConditionRow(
    condition: Condition,
    field: FieldDef,
    fields: List<FieldDef>,
    choices: List<String>,
    onChange: (Condition) -> Unit,
    onRemove: () -> Unit
) {
    val kind = field.valueKind
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                MenuButton(field.name, fields.map { it to it.name }, showAddIcon = false) {
                    onChange(condition.copy(fieldId = it.id))
                }
                val opLabels = kind.ops.associateWith { stringResource(opLabel(it, kind)) }
                MenuButton(opLabels.getValue(condition.op), opLabels.toList(), showAddIcon = false) {
                    onChange(condition.copy(op = it))
                }
            }
            TextButton(onClick = onRemove) { Text(stringResource(R.string.action_remove)) }
        }
        if (condition.op.needsValue) ConditionValue(condition, field, choices, onChange)
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

/** The value a condition compares with, entered to suit the field's kind. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConditionValue(
    condition: Condition,
    field: FieldDef,
    choices: List<String>,
    onChange: (Condition) -> Unit
) {
    val setValue = { value: String -> onChange(condition.copy(value = value)) }
    val label = stringResource(R.string.filter_value)
    when (field.valueKind) {
        ValueKind.DATE, ValueKind.TIMESTAMP -> DateEditor(label, condition.value, false, setValue, Modifier)
        ValueKind.TIME -> TimeEditor(label, condition.value, false, setValue, Modifier)
        ValueKind.CHOICES -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { choice ->
                val chosen = choice in condition.choices
                FilterChip(
                    selected = chosen,
                    onClick = {
                        onChange(
                            condition.copy(
                                choices = if (chosen) {
                                    condition.choices - choice
                                } else {
                                    condition.choices +
                                        choice
                                }
                            )
                        )
                    },
                    label = { Text(choice) }
                )
            }
        }
        else -> Row(verticalAlignment = Alignment.CenterVertically) {
            val number = field.valueKind == ValueKind.NUMBER
            OutlinedTextField(
                value = condition.value,
                onValueChange = { setValue(if (number) FieldValues.filterNumber(it, decimal = true) else it) },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text
                ),
                modifier = Modifier.weight(1f)
            )
            // A single-choice List's choices can be picked rather than typed.
            MenuButton(
                stringResource(R.string.filter_pick),
                choices.map {
                    it to it
                },
                showAddIcon = false,
                onPick = setValue
            )
        }
    }
}

/** Operator labels; some read differently for dates/times, numbers and multiple choices. */
private val baseLabels = mapOf(
    Op.CONTAINS to R.string.op_contains,
    Op.NOT_CONTAINS to R.string.op_not_contains,
    Op.IS to R.string.op_is,
    Op.IS_NOT to R.string.op_is_not,
    Op.STARTS_WITH to R.string.op_starts_with,
    Op.LESS to R.string.op_less,
    Op.LESS_OR_EQUAL to R.string.op_at_most,
    Op.GREATER to R.string.op_greater,
    Op.GREATER_OR_EQUAL to R.string.op_at_least,
    Op.EMPTY to R.string.op_empty,
    Op.NOT_EMPTY to R.string.op_not_empty,
    Op.CHECKED to R.string.op_checked,
    Op.UNCHECKED to R.string.op_unchecked,
    Op.ANY_OF to R.string.op_any_of,
    Op.ALL_OF to R.string.op_all_of,
    Op.NONE_OF to R.string.op_none_of
)
private val orderedLabels = mapOf(
    Op.LESS to R.string.op_before,
    Op.LESS_OR_EQUAL to R.string.op_on_or_before,
    Op.GREATER to R.string.op_after,
    Op.GREATER_OR_EQUAL to R.string.op_on_or_after
)
private val numberLabels = mapOf(Op.IS to R.string.op_equals, Op.IS_NOT to R.string.op_not_equals)
private val choiceLabels = mapOf(Op.EMPTY to R.string.op_no_choices, Op.NOT_EMPTY to R.string.op_has_choices)

private fun opLabel(op: Op, kind: ValueKind): Int {
    val special = when (kind) {
        ValueKind.DATE, ValueKind.TIME, ValueKind.TIMESTAMP -> orderedLabels
        ValueKind.NUMBER -> numberLabels
        ValueKind.CHOICES -> choiceLabels
        else -> emptyMap()
    }
    return special[op] ?: baseLabels.getValue(op)
}
