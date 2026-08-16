package com.kvk.leh.seabuckthorn.ui.survey.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.kvk.leh.seabuckthorn.domain.calculations.DescriptiveStatistics
import com.kvk.leh.seabuckthorn.domain.model.GpsStatus
import com.kvk.leh.seabuckthorn.ui.components.SectionCard

@Composable
fun Step8ReviewScreen(state: SurveyFormState, modifier: Modifier = Modifier) {
    val lengths = state.berries.mapNotNull { it.lengthMm }
    val widths = state.berries.mapNotNull { it.widthMm }
    val meanLength = DescriptiveStatistics.summarize(lengths).mean
    val meanWidth = DescriptiveStatistics.summarize(widths).mean

    val warnings = buildList {
        if (state.gpsStatus != GpsStatus.CAPTURED) add("GPS location was not captured — coordinates will be saved as unavailable.")
        if (state.tssBrix == null) add("TSS (°Brix) has not been entered — it is the primary fruit quality measurement.")
        val total = state.maturityPercentTotal
        val hasAnyPercent = listOfNotNull(state.pctUnripe, state.pctIntermediate, state.pctRipe, state.pctOverripe).isNotEmpty()
        if (hasAnyPercent && kotlin.math.abs(total - 100.0) > 1.0) add("Maturity percentages total ${"%.0f".format(total)}%, not ~100%.")
        if (state.dominantMaturityStage == null) add("No dominant maturity stage selected.")
        if (state.photos.isEmpty()) add("No photographs attached to this survey.")
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(title = "Review Before Save", subtitle = "Please check the summary below before saving this survey.") {
                ReviewRow("Survey ID", state.surveyCode)
                ReviewRow("Location", listOf(state.siteName, state.village, state.block, state.district).filter { it.isNotBlank() }.joinToString(", ").ifBlank { "—" })
                ReviewRow("Latitude", state.latitude?.let { "%.6f°".format(it) } ?: "Unavailable")
                ReviewRow("Longitude", state.longitude?.let { "%.6f°".format(it) } ?: "Unavailable")
                ReviewRow("Altitude", state.altitude?.let { "%.1f m".format(it) } ?: "Unavailable")
                ReviewRow("Maturity", state.dominantMaturityStage?.label ?: "Not selected")
                ReviewRow("Berry length", meanLength?.let { "%.2f mm (mean)".format(it) } ?: "—")
                ReviewRow("Berry diameter", meanWidth?.let { "%.2f mm (mean)".format(it) } ?: "—")
                ReviewRow("TSS", state.tssBrix?.let { "%.1f °Brix".format(it) } ?: "—")
                ReviewRow("Shrub height", state.plantHeightM?.let { "%.2f m".format(it) } ?: "—")
                ReviewRow("Photos", state.photos.size.toString())
            }
        }
        if (warnings.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text(
                                "  Please review",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        warnings.forEach {
                            Text("• $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        Text(
                            "These are warnings, not blockers — you can still save the survey.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
