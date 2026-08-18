package com.kvkleh.sbtsurvey.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kvkleh.sbtsurvey.export.ExportFormat

enum class ExportSheetMode { EXPORT, SHARE }

/**
 * Format chooser shared by Export Data and Share Data.
 *
 * Both entry points produce the same artefacts; only what happens afterwards differs —
 * Export leaves the file on the device, Share hands it to the Android Sharesheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    mode: ExportSheetMode,
    busy: Boolean,
    surveyCount: Int,
    onDismiss: () -> Unit,
    onChoose: (ExportFormat) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (mode == ExportSheetMode.SHARE) "Share Data" else "Export Data",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = if (surveyCount == 0) {
                    "There are no surveys to export yet."
                } else {
                    "$surveyCount survey${if (surveyCount == 1) "" else "s"} will be included."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(4.dp))

            if (busy) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(16.dp))
                    Text("Preparing export…")
                }
            } else {
                FormatOption(
                    icon = Icons.Filled.TableChart,
                    title = "Export CSV",
                    subtitle = "All survey fields as a comma-separated file",
                    enabled = surveyCount > 0
                ) { onChoose(ExportFormat.CSV) }

                FormatOption(
                    icon = Icons.Filled.GridOn,
                    title = "Export Excel (.xlsx)",
                    subtitle = "Same fields as a spreadsheet workbook",
                    enabled = surveyCount > 0
                ) { onChoose(ExportFormat.XLSX) }

                FormatOption(
                    icon = Icons.Filled.FolderZip,
                    title = "Complete Survey Package (.zip)",
                    subtitle = "CSV + Excel + all photographs + README",
                    enabled = surveyCount > 0
                ) { onChoose(ExportFormat.PACKAGE) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
