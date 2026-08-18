package com.kvkleh.sbtsurvey.ui.map

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kvkleh.sbtsurvey.map.BaseMapLayer
import com.kvkleh.sbtsurvey.map.MapExportFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Basemap chooser.
 *
 * Each option explains what it shows and where it comes from, because the choice has real
 * consequences in the field: satellite imagery needs a connection the first time it is
 * viewed, while the offline grid never does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseMapChooserSheet(
    selected: BaseMapLayer,
    cacheSize: () -> Long,
    onSelect: (BaseMapLayer) -> Unit,
    onClearCache: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Measuring the cache touches the disk, so it is done off the main thread.
    val bytes by produceState(initialValue = -1L) {
        value = withContext(Dispatchers.IO) { runCatching { cacheSize() }.getOrDefault(0L) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Basemap", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Survey locations are always drawn from this device. " +
                    "Imagery is downloaded once and then kept for offline use.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(4.dp))

            BaseMapLayer.selectable.forEach { layer ->
                LayerOption(
                    icon = iconFor(layer),
                    title = layer.label,
                    subtitle = layer.description,
                    attribution = layer.attribution,
                    selected = layer == selected,
                    onClick = { onSelect(layer) }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        bytes < 0 -> "Saved imagery: measuring…"
                        bytes == 0L -> "No imagery saved on this device yet"
                        else -> "Saved imagery: ${formatBytes(bytes)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (bytes > 0) {
                    TextButton(onClick = onClearCache) { Text("Clear") }
                }
            }
        }
    }
}

/** Format chooser for saving the map sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapExportSheet(
    markerCount: Int,
    layerLabel: String,
    onDismiss: () -> Unit,
    onChoose: (MapExportFormat, Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Save survey map", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = if (markerCount == 0) {
                    "No survey has coordinates yet, so there is nothing to map."
                } else {
                    "$markerCount location${if (markerCount == 1) "" else "s"} over the " +
                        "$layerLabel basemap, at the extent currently on screen."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(4.dp))

            MapExportFormat.entries.forEach { format ->
                ExportOption(
                    icon = iconFor(format),
                    format = format,
                    enabled = markerCount > 0,
                    onSave = { onChoose(format, false) },
                    onShare = { onChoose(format, true) }
                )
            }

            Text(
                text = "The GeoTIFF carries its coordinate system (EPSG:3857) inside the " +
                    "file, so it drops straight into ArcGIS or QGIS with no georeferencing " +
                    "step. It holds the map face only — no title or legend — because every " +
                    "pixel in it has to be a real ground position.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayerOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    attribution: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 78.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = attribution,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
private fun ExportOption(
    icon: ImageVector,
    format: MapExportFormat,
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
                Text(format.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = format.description,
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

private fun iconFor(layer: BaseMapLayer): ImageVector = when (layer) {
    BaseMapLayer.SATELLITE -> Icons.Filled.Satellite
    BaseMapLayer.TERRAIN -> Icons.Filled.Terrain
    BaseMapLayer.STREET -> Icons.Filled.Map
    BaseMapLayer.GRID -> Icons.Filled.GridOn
}

private fun iconFor(format: MapExportFormat): ImageVector = when (format) {
    MapExportFormat.PDF -> Icons.Filled.PictureAsPdf
    MapExportFormat.GEOTIFF -> Icons.Filled.Public
    MapExportFormat.JPEG -> Icons.Filled.Image
    MapExportFormat.PNG -> Icons.Filled.Image
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
    else -> String.format(Locale.US, "%.0f kB", bytes / 1024.0)
}
