package com.kvk.leh.seabuckthorn.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kvk.leh.seabuckthorn.data.local.entity.SurveyEntity
import com.kvk.leh.seabuckthorn.ui.theme.BerryOrange40

/**
 * Offline coordinate-based visualization of survey locations: a simple scatter plot of
 * latitude/longitude (no map tiles, so no internet or offline basemap dependency) plus a
 * coordinate list. The plot's own coordinate frame is designed so a real offline basemap layer
 * can be dropped in underneath later without changing how points are placed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyMapScreen(viewModel: SurveyMapViewModel, onOpenSurvey: (String) -> Unit, onBack: () -> Unit) {
    val surveys by viewModel.surveysWithGps.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Survey Map (${surveys.size} points)") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (surveys.isEmpty()) {
                Text(
                    "No surveys with GPS coordinates yet.",
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                CoordinateScatterPlot(surveys = surveys, modifier = Modifier.fillMaxWidth().height(280.dp).padding(16.dp))
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(surveys, key = { it.id }) { survey ->
                        Card(onClick = { onOpenSurvey(survey.id) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(survey.surveyCode, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${survey.village.ifBlank { "—" }} · %.6f°, %.6f°".format(survey.latitude, survey.longitude),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "Altitude: ${survey.altitude?.let { "%.0f m".format(it) } ?: "—"}   Accuracy: ${survey.gpsAccuracyM?.let { "±%.1f m".format(it) } ?: "—"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoordinateScatterPlot(surveys: List<SurveyEntity>, modifier: Modifier = Modifier) {
    val lats = surveys.mapNotNull { it.latitude }
    val lons = surveys.mapNotNull { it.longitude }
    if (lats.isEmpty() || lons.isEmpty()) return
    val minLat = lats.min(); val maxLat = lats.max()
    val minLon = lons.min(); val maxLon = lons.max()
    val latRange = (maxLat - minLat).takeIf { it > 0.0001 } ?: 0.0001
    val lonRange = (maxLon - minLon).takeIf { it > 0.0001 } ?: 0.0001

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val margin = 12.dp.toPx()
            surveys.forEach { survey ->
                val lat = survey.latitude ?: return@forEach
                val lon = survey.longitude ?: return@forEach
                val x = margin + ((lon - minLon) / lonRange).toFloat() * (size.width - margin * 2)
                // Latitude increases northward, but canvas Y increases downward, so invert.
                val y = margin + (1f - ((lat - minLat) / latRange).toFloat()) * (size.height - margin * 2)
                drawCircle(color = BerryOrange40, radius = 7.dp.toPx(), center = Offset(x, y))
                drawCircle(color = Color.White, radius = 7.dp.toPx(), center = Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
            }
        }
    }
}
