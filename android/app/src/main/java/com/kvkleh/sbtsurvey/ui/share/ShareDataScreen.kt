package com.kvkleh.sbtsurvey.ui.share

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kvkleh.sbtsurvey.data.export.ExportFormat
import com.kvkleh.sbtsurvey.ui.components.FormColumn
import com.kvkleh.sbtsurvey.ui.components.SbtRadioGroup
import com.kvkleh.sbtsurvey.ui.components.SbtSegmentedToggle
import com.kvkleh.sbtsurvey.ui.components.SectionCard
import com.kvkleh.sbtsurvey.ui.export.ExportViewModel

/**
 * Sends the survey data to whichever app the surveyor picks — WhatsApp, Gmail,
 * Telegram, Drive, Bluetooth, anything installed.
 *
 * This is deliberately separate from Export Data: exporting writes a file to a
 * chosen folder, whereas sharing hands the file to another app. In the field the
 * second is what actually gets data back to the office, so it is one screen and
 * two taps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareDataScreen(
    onBack: () -> Unit,
    viewModel: ExportViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share Data") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        FormColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(16.dp)
                    )
                    .padding(18.dp)
            ) {
                Text(
                    text = "${state.exportCount}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "record(s) will be sent",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ShareTargetHint(Icons.Filled.Chat, "WhatsApp")
                    ShareTargetHint(Icons.Filled.Email, "Gmail")
                    ShareTargetHint(Icons.AutoMirrored.Filled.Send, "Telegram")
                }
            }

            SectionCard(number = null, title = "File format") {
                SbtRadioGroup(
                    options = ExportFormat.entries.map { it.label },
                    selected = state.format.label,
                    onSelected = { label ->
                        ExportFormat.entries.firstOrNull { it.label == label }
                            ?.let(viewModel::setFormat)
                    }
                )
                Text(
                    text = state.format.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard(number = null, title = "What to send") {
                SbtSegmentedToggle(
                    options = listOf(SEND_DATA_ONLY, SEND_WITH_PHOTOS),
                    labels = listOf("Data only", "Data + photos"),
                    selected = if (state.includePhotos) SEND_WITH_PHOTOS else SEND_DATA_ONLY,
                    onSelected = { viewModel.setIncludePhotos(it == SEND_WITH_PHOTOS) }
                )
                Text(
                    text = if (state.includePhotos) {
                        "One ZIP holding the data file and every photograph, each named " +
                            "after its Survey ID. Larger, but nothing is left behind."
                    } else {
                        "Just the ${state.format.label} file. Small enough for a slow " +
                            "connection."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.draftCount > 0) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Include unfinished draft",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Marked \"Draft\" in the Record Status column.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.includeDrafts,
                            onCheckedChange = viewModel::setIncludeDrafts
                        )
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.shareCurrentSelection { intent ->
                        context.startActivity(
                            Intent.createChooser(intent, "Send survey data with…")
                        )
                    }
                },
                enabled = !state.busy && state.exportCount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Share Survey Data", style = MaterialTheme.typography.titleMedium)
                }
            }

            state.statusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text(
                text = "The app you choose decides what happens next. Nothing is uploaded " +
                    "by this app itself, and nothing leaves the device until you pick an app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ShareTargetHint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

private const val SEND_DATA_ONLY = "data"
private const val SEND_WITH_PHOTOS = "bundle"
