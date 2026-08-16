package com.kvk.leh.seabuckthorn.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** Tap-to-open time picker bound to an "HH:mm" formatted string. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerField(label: String, time: String, onTimeSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }
    val parts = time.split(":").mapNotNull { it.toIntOrNull() }
    val initialHour = parts.getOrNull(0) ?: 0
    val initialMinute = parts.getOrNull(1) ?: 0

    OutlinedTextField(
        value = time,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = { showDialog = true }) {
                Icon(Icons.Filled.AccessTime, contentDescription = "Pick time")
            }
        },
        modifier = modifier.fillMaxWidth()
    )
    if (showDialog) {
        val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
        Dialog(onDismissRequest = { showDialog = false }) {
            androidx.compose.material3.Surface(shape = androidx.compose.material3.MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(20.dp)) {
                    TimePicker(state = state)
                    Button(
                        onClick = {
                            onTimeSelected("%02d:%02d".format(state.hour, state.minute))
                            showDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("OK") }
                    TextButton(onClick = { showDialog = false }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }
            }
        }
    }
}
