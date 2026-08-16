package com.kvk.leh.seabuckthorn.ui.survey.wizard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kvk.leh.seabuckthorn.domain.model.GpsStatus
import com.kvk.leh.seabuckthorn.ui.components.SectionCard
import com.kvk.leh.seabuckthorn.util.DateUtils
import kotlin.math.min

@Composable
fun Step2LocationScreen(
    state: SurveyFormState,
    isCapturingGps: Boolean,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onMarkUnavailable: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(
                title = "Automatic GPS Capture",
                subtitle = "Uses the device's GPS chip only — works fully offline, no data connection required."
            ) {
                GpsAccuracyPreview(state = state, isCapturing = isCapturingGps)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCapturingGps) {
                        Button(onClick = onStopCapture, modifier = Modifier.weight(1f)) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                            Text("Stop & Use This Fix")
                        }
                    } else {
                        Button(onClick = onStartCapture, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.GpsFixed, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Capture GPS")
                        }
                    }
                    OutlinedButton(
                        onClick = onMarkUnavailable,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.GpsOff, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Unavailable")
                    }
                }

                if (state.gpsStatus == GpsStatus.UNAVAILABLE) {
                    Text(
                        "GPS not captured for this survey. The location fields will be saved as unavailable rather than an invented value.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun GpsAccuracyPreview(state: SurveyFormState, isCapturing: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.size(140.dp)) {
            val accuracy = state.gpsAccuracyM
            val radiusFraction = if (accuracy != null) (1f - min(accuracy, 50f) / 50f).coerceIn(0.15f, 1f) else 0.3f
            drawCircle(color = Color(0xFF4B6B2F).copy(alpha = 0.15f), radius = size.minDimension / 2 * radiusFraction)
            drawCircle(color = Color(0xFF4B6B2F), radius = 8.dp.toPx())
            drawCircle(color = Color(0xFF4B6B2F), radius = size.minDimension / 2 * radiusFraction, style = Stroke(width = 2.dp.toPx()))
        }
        Spacer(Modifier.height(8.dp))
        val accuracyText = state.gpsAccuracyM?.let { "±${"%.1f".format(it)} m" } ?: "No fix yet"
        Text(
            "GPS Accuracy: $accuracyText",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (isCapturing) {
            Text("Improving accuracy… tap \"Stop & Use This Fix\" when satisfied", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(12.dp))
        LocationRow("Latitude", state.latitude?.let { "%.6f°".format(it) } ?: "—")
        LocationRow("Longitude", state.longitude?.let { "%.6f°".format(it) } ?: "—")
        LocationRow("Altitude", state.altitude?.let { "%.1f m".format(it) } ?: "—")
        LocationRow("Acquired", state.gpsAcquiredAt?.let { DateUtils.epochMillisToDisplayDateTime(it) } ?: "—")
    }
}

@Composable
private fun LocationRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
