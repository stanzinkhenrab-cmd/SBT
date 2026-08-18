package com.kvkleh.sbtsurvey.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kvkleh.sbtsurvey.export.ExportFormat

/**
 * Export chooser.
 *
 * One sheet covers both outcomes: **Save** writes the file wherever the surveyor picks
 * through the system document picker, and **Share** hands it to the Android Sharesheet.
 * They produce exactly the same file, which is why they belong on one screen rather than
 * behind two separate menu entries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    busy: Boolean,
    surveyCount: Int,
    photoCount: Int,
    onDismiss: () -> Unit,
    onChoose: (ExportFormat, Boolean) -> Unit
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
            Text("Export Data", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = if (surveyCount == 0) {
                    "There are no surveys to export yet."
                } else {
                    "$surveyCount survey${if (surveyCount == 1) "" else "s"}" +
                        (if (photoCount > 0) " and $photoCount photograph${if (photoCount == 1) "" else "s"}" else "") +
                        " will be included."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(2.dp))

            if (busy) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
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
                    title = "CSV (.csv)",
                    subtitle = "All survey fields as a comma-separated file",
                    enabled = surveyCount > 0,
                    onSave = { onChoose(ExportFormat.CSV, false) },
                    onShare = { onChoose(ExportFormat.CSV, true) }
                )
                FormatOption(
                    icon = Icons.Filled.GridOn,
                    title = "Excel (.xlsx)",
                    subtitle = "Same fields as a spreadsheet workbook",
                    enabled = surveyCount > 0,
                    onSave = { onChoose(ExportFormat.XLSX, false) },
                    onShare = { onChoose(ExportFormat.XLSX, true) }
                )
                FormatOption(
                    icon = Icons.Filled.FolderZip,
                    title = "Complete Survey Package (.zip)",
                    subtitle = "CSV + Excel + every photograph + a dataset README",
                    enabled = surveyCount > 0,
                    onSave = { onChoose(ExportFormat.PACKAGE, false) },
                    onShare = { onChoose(ExportFormat.PACKAGE, true) }
                )

                Text(
                    text = "Save writes the file to a folder you choose. Share sends it " +
                        "through WhatsApp, email, Telegram, Drive or any other app on this " +
                        "device.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun FormatOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onSave, enabled = enabled) { Text("Save") }
            TextButton(onClick = onShare, enabled = enabled) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share")
            }
        }
    }
}
