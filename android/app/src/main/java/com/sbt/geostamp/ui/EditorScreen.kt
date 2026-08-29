package com.sbt.geostamp.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sbt.geostamp.model.StampContent
import com.sbt.geostamp.model.StampOptions
import com.sbt.geostamp.model.Template
import androidx.compose.foundation.Image

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: EditorState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onRefreshLocation: () -> Unit,
    onContentChange: ((StampContent) -> StampContent) -> Unit,
    onOptionsChange: ((StampOptions) -> StampOptions) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Edit stamp") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = onShare, enabled = !state.isSaving) {
                    Icon(Icons.Default.Share, contentDescription = "Share")
                }
            }
        )

        PreviewPane(
            preview = state.preview,
            busy = state.isBusy,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TemplateRow(state.options.template) { template ->
                onOptionsChange { it.copy(template = template) }
            }

            LocationRow(
                state = state,
                onRefreshLocation = onRefreshLocation
            )

            OutlinedTextField(
                value = state.content.title,
                onValueChange = { value -> onContentChange { it.copy(title = value) } },
                label = { Text("Place") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.content.addressLine,
                onValueChange = { value -> onContentChange { it.copy(addressLine = value) } },
                label = { Text("Address") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.content.note,
                onValueChange = { value -> onContentChange { it.copy(note = value) } },
                label = { Text("Note") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            SectionLabel("Show on the stamp")
            ToggleRow("Map thumbnail", state.options.showMap) { value ->
                onOptionsChange { it.copy(showMap = value) }
            }
            ToggleRow("QR code to the map link", state.options.showQr) { value ->
                onOptionsChange { it.copy(showQr = value) }
            }
            ToggleRow("Coordinates", state.options.showCoordinates) { value ->
                onOptionsChange { it.copy(showCoordinates = value) }
            }
            ToggleRow("Date and time", state.options.showDateTime) { value ->
                onOptionsChange { it.copy(showDateTime = value) }
            }
            ToggleRow("Note line", state.options.showNote) { value ->
                onOptionsChange { it.copy(showNote = value) }
            }
            ToggleRow("Degrees / minutes / seconds", state.options.coordinatesAsDms) { value ->
                onOptionsChange { it.copy(coordinatesAsDms = value) }
            }
            ToggleRow("24-hour clock", state.options.use24Hour) { value ->
                onOptionsChange { it.copy(use24Hour = value) }
            }

            SectionLabel("Accent")
            AccentRow(state.options.accentColor) { color ->
                onOptionsChange { it.copy(accentColor = color) }
            }

            SectionLabel("Stamp size")
            Slider(
                value = state.options.scale,
                onValueChange = { value -> onOptionsChange { it.copy(scale = value) } },
                valueRange = 0.75f..1.4f,
                steps = 12
            )

            if (state.options.showMap) {
                SectionLabel("Map zoom — ${state.options.mapZoom}")
                Slider(
                    value = state.options.mapZoom.toFloat(),
                    onValueChange = { value -> onOptionsChange { it.copy(mapZoom = value.toInt()) } },
                    valueRange = 6f..18f,
                    steps = 11
                )
            }

            Spacer(Modifier.height(4.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onShare,
                enabled = !state.isSaving && state.hasPhoto,
                modifier = Modifier.weight(1f).height(52.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Share")
            }
            Button(
                onClick = onSave,
                enabled = !state.isSaving && state.hasPhoto,
                modifier = Modifier.weight(1f).height(52.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Download")
                }
            }
        }
    }
}

@Composable
private fun PreviewPane(preview: Bitmap?, busy: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        preview?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Stamped photo preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
            )
        }
        if (busy || preview == null) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun TemplateRow(selected: Template, onSelect: (Template) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Template.entries.forEach { template ->
            FilterChip(
                selected = template == selected,
                onClick = { onSelect(template) },
                label = { Text(template.label) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationRow(state: EditorState, onRefreshLocation: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val label = when {
            state.isLocating -> "Getting a fix…"
            state.content.hasLocation -> state.content.formattedCoordinates(false)
            else -> "No location yet"
        }
        AssistChip(
            onClick = onRefreshLocation,
            enabled = !state.isLocating,
            label = { Text(label, maxLines = 1) },
            leadingIcon = {
                if (state.isLocating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.MyLocation, contentDescription = null)
                }
            },
            colors = AssistChipDefaults.assistChipColors(),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AccentRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StampOptions.ACCENTS.forEach { accent ->
            val isSelected = accent == selected
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(accent))
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape
                    )
                    .clickable { onSelect(accent) }
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
