package com.kvkleh.sbtsurvey.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kvkleh.sbtsurvey.domain.SurveyOption
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Standard text input for the survey form: tall touch target, clear required marker. */
@Composable
fun SbtTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    errorText: String? = null,
    supportingText: String? = null,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    enabled: Boolean = true,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp),
        label = { Text(if (required) "$label *" else label) },
        singleLine = singleLine,
        enabled = enabled,
        isError = errorText != null,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        supportingText = when {
            errorText != null -> {
                { Text(errorText, color = MaterialTheme.colorScheme.error) }
            }
            supportingText != null -> {
                { Text(supportingText) }
            }
            else -> null
        },
        trailingIcon = trailingIcon
    )
}

/** Numeric input that always brings up the decimal keypad. */
@Composable
fun SbtNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    errorText: String? = null,
    supportingText: String? = null,
    suffix: String? = null
) {
    SbtTextField(
        label = if (suffix != null) "$label ($suffix)" else label,
        value = value,
        onValueChange = { new -> onValueChange(new.filter { it.isDigit() || it == '.' }) },
        modifier = modifier,
        required = required,
        errorText = errorText,
        supportingText = supportingText,
        keyboardType = KeyboardType.Decimal
    )
}

/**
 * Dropdown that also accepts free text, so a village or block that is not in the
 * reference list never blocks data entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SbtDropdownField(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    errorText: String? = null,
    allowFreeText: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (allowFreeText) onValueChange(it) },
            readOnly = !allowFreeText,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp)
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryEditable, true),
            label = { Text(if (required) "$label *" else label) },
            singleLine = true,
            isError = errorText != null,
            shape = MaterialTheme.shapes.small,
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "Show $label options"
                )
            },
            supportingText = errorText?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
        )

        if (options.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, style = MaterialTheme.typography.bodyLarge) },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 18.dp,
                            vertical = 12.dp
                        )
                    )
                }
            }
        }
    }
}

/** Selectable option cards used for shrub type, maturity stage, ease of harvest, shape. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T : SurveyOption> OptionChips(
    label: String,
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    errorText: String? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = if (required) "$label *" else label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.padding(top = 8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(option) },
                    label = {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        null
                    },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.heightIn(min = 52.dp)
                )
            }
        }
        if (errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/** Two-state unit selector, e.g. metres vs feet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : SurveyOption> UnitSelector(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(option.label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Date field backed by the Material 3 date picker; may be left blank. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SbtDateField(
    label: String,
    isoDate: String?,
    onDateChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null
) {
    var showPicker by remember { mutableStateOf(false) }

    // The text field itself is disabled so it never opens a keyboard; the surrounding Box
    // takes the touch and opens the picker instead.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showPicker = true }
    ) {
        OutlinedTextField(
            value = isoDate.orEmpty(),
            onValueChange = { },
            readOnly = true,
            enabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp),
            label = { Text(label) },
            placeholder = { Text("Not harvested yet") },
            shape = MaterialTheme.shapes.small,
            supportingText = supportingText?.let { { Text(it) } },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isoDate.isNullOrBlank()) {
                        IconButton(onClick = { onDateChange(null) }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear $label")
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = "Pick $label",
                        modifier = Modifier.padding(end = 14.dp)
                    )
                }
            },
            colors = disabledButReadableTextFieldColors()
        )
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = isoDate?.let { parseIsoToUtcMillis(it) }
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onDateChange(state.selectedDateMillis?.let { formatUtcMillisToIso(it) })
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun disabledButReadableTextFieldColors() =
    androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        disabledTextColor = MaterialTheme.colorScheme.onSurface,
        disabledBorderColor = MaterialTheme.colorScheme.outline,
        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

private val isoFormat: SimpleDateFormat
    get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

/** The Material date picker works in UTC millis; survey dates are plain ISO calendar dates. */
fun parseIsoToUtcMillis(iso: String): Long? =
    runCatching { isoFormat.parse(iso)?.time }.getOrNull()

fun formatUtcMillisToIso(millis: Long): String = isoFormat.format(java.util.Date(millis))
