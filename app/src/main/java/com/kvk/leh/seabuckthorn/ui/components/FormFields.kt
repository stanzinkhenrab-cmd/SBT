package com.kvk.leh.seabuckthorn.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

/** A free-text field with an optional inline validation message. */
@Composable
fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    singleLine: Boolean = true,
    supportingText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        isError = errorText != null,
        supportingText = {
            val text = errorText ?: supportingText
            if (text != null) Text(text)
        },
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * A numeric field displaying its unit inline (e.g. "Plant height (m)") and reporting the
 * parsed [Double] value back to the caller — null when the field is empty or unparsable.
 */
@Composable
fun LabeledNumberField(
    label: String,
    unit: String?,
    value: Double?,
    onValueChange: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    allowDecimal: Boolean = true
) {
    var text by remember(value) { mutableStateOf(value?.let { formatForEditing(it) } ?: "") }
    val fullLabel = if (unit.isNullOrBlank()) label else "$label ($unit)"
    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            val sanitized = if (allowDecimal) {
                newText.filter { it.isDigit() || it == '.' || it == '-' }
            } else {
                newText.filter { it.isDigit() || it == '-' }
            }
            text = sanitized
            onValueChange(sanitized.toDoubleOrNull())
        },
        label = { Text(fullLabel) },
        singleLine = true,
        isError = errorText != null,
        supportingText = { if (errorText != null) Text(errorText) },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number
        ),
        modifier = modifier.fillMaxWidth()
    )
}

private fun formatForEditing(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/** Read-only field showing a value the app fills in automatically (e.g. GPS coordinates). */
@Composable
fun ReadOnlyValueField(label: String, value: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth()
    )
}

/** Dropdown for a controlled vocabulary (enum) field. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> LabeledDropdown(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selected?.let(optionLabel) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, modifier = modifier)
}
