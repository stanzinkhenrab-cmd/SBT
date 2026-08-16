package com.kvk.leh.seabuckthorn.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kvk.leh.seabuckthorn.util.DateUtils

/** A tap-to-open Material3 date picker bound to an epoch-day value. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    label: String,
    epochDay: Long?,
    onDateSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = epochDay?.let { DateUtils.epochDayToDisplay(it) } ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = { showDialog = true }) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = "Pick date")
            }
        },
        modifier = modifier.fillMaxWidth()
    )
    if (showDialog) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = DateUtils.epochDayToUtcMillis(epochDay ?: DateUtils.todayEpochDay())
        )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDateSelected(DateUtils.utcMillisToEpochDay(it)) }
                    showDialog = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = state)
        }
    }
}

/** Same as [DatePickerField] but works with nullable epoch-millis (used by phenological dates). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionalDatePickerField(
    label: String,
    epochMillis: Long?,
    onDateSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = DateUtils.epochMillisToDisplayDate(epochMillis).takeIf { epochMillis != null } ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = { showDialog = true }) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = "Pick date")
            }
        },
        modifier = modifier.fillMaxWidth()
    )
    if (showDialog) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = DateUtils.epochMillisToUtcMillisForPicker(epochMillis)
                ?: DateUtils.epochDayToUtcMillis(DateUtils.todayEpochDay())
        )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        val epochDay = DateUtils.utcMillisToEpochDay(millis)
                        onDateSelected(DateUtils.localDateToEpochMillis(java.time.LocalDate.ofEpochDay(epochDay)))
                    }
                    showDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onDateSelected(null)
                    showDialog = false
                }) { Text("Clear") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}
