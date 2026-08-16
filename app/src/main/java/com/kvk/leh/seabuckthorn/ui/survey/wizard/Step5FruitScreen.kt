package com.kvk.leh.seabuckthorn.ui.survey.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kvk.leh.seabuckthorn.domain.calculations.DescriptiveStatistics
import com.kvk.leh.seabuckthorn.domain.model.*
import com.kvk.leh.seabuckthorn.ui.components.LabeledDropdown
import com.kvk.leh.seabuckthorn.ui.components.LabeledNumberField
import com.kvk.leh.seabuckthorn.ui.components.LabeledTextField
import com.kvk.leh.seabuckthorn.ui.components.ReadOnlyValueField
import com.kvk.leh.seabuckthorn.ui.components.SectionCard

@Composable
fun Step5FruitScreen(
    state: SurveyFormState,
    onUpdate: ((SurveyFormState) -> SurveyFormState) -> Unit,
    onAddBerry: () -> Unit,
    onRemoveBerry: (Int) -> Unit,
    onUpdateBerry: (BerryRow) -> Unit,
    modifier: Modifier = Modifier
) {
    val lengths = state.berries.mapNotNull { it.lengthMm }
    val widths = state.berries.mapNotNull { it.widthMm }
    val weights = state.berries.mapNotNull { it.weightG }
    val lengthStats = DescriptiveStatistics.summarize(lengths)
    val widthStats = DescriptiveStatistics.summarize(widths)
    val weightStats = DescriptiveStatistics.summarize(weights)

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(title = "Berry Measurements", subtitle = "Measure individual berries from the sample (Berry 1, 2, 3…).") {
                state.berries.forEach { row ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("#${row.index}", modifier = Modifier.width(28.dp), style = MaterialTheme.typography.labelLarge)
                        LabeledNumberField("Length", "mm", row.lengthMm, { v -> onUpdateBerry(row.copy(lengthMm = v)) }, modifier = Modifier.weight(1f))
                        LabeledNumberField("Width", "mm", row.widthMm, { v -> onUpdateBerry(row.copy(widthMm = v)) }, modifier = Modifier.weight(1f))
                        LabeledNumberField("Weight", "g", row.weightG, { v -> onUpdateBerry(row.copy(weightG = v)) }, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemoveBerry(row.index) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove berry ${row.index}")
                        }
                    }
                }
                OutlinedButton(onClick = onAddBerry, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("  Add berry (${state.berries.size} measured)")
                }
                HorizontalDivider()
                Text("Computed summary", style = MaterialTheme.typography.titleSmall)
                StatSummaryRow("Length (mm)", lengthStats)
                StatSummaryRow("Diameter/width (mm)", widthStats)
                StatSummaryRow("Weight (g)", weightStats)
            }
        }
        item {
            SectionCard(title = "Fruit Quality") {
                LabeledDropdown("Fruit colour", FruitColor.entries, state.fruitColor, { it.label }, { v -> onUpdate { it.copy(fruitColor = v) } })
                if (state.fruitColor == FruitColor.OTHER) {
                    LabeledTextField("Specify fruit colour", state.fruitColorOther, { v -> onUpdate { it.copy(fruitColorOther = v) } })
                }
                LabeledDropdown("Fruit firmness", FruitFirmness.entries, state.fruitFirmness, { it.label }, { v -> onUpdate { it.copy(fruitFirmness = v) } })
                LabeledDropdown("Fruit shape", FruitShape.entries, state.fruitShape, { it.label }, { v -> onUpdate { it.copy(fruitShape = v) } })
                if (state.fruitShape == FruitShape.OTHER) {
                    LabeledTextField("Specify fruit shape", state.fruitShapeOther, { v -> onUpdate { it.copy(fruitShapeOther = v) } })
                }
                LabeledNumberField("Berries per cluster", null, state.berriesPerCluster, { v -> onUpdate { it.copy(berriesPerCluster = v) } })
            }
        }
        item {
            SectionCard(title = "Juice / Laboratory Quality", subtitle = "TSS (°Brix) is the primary required quality field.") {
                LabeledNumberField("TSS", "°Brix", state.tssBrix, { v -> onUpdate { it.copy(tssBrix = v) } })
                LabeledNumberField("pH", null, state.ph, { v -> onUpdate { it.copy(ph = v) } })
                LabeledNumberField("Juice yield", "%", state.juiceYieldPercent, { v -> onUpdate { it.copy(juiceYieldPercent = v) } })
                LabeledNumberField("Titratable acidity (optional)", "%", state.titratableAcidityPercent, { v -> onUpdate { it.copy(titratableAcidityPercent = v) } })
                LabeledNumberField("Vitamin C (optional)", "mg/100g", state.vitaminCMgPer100g, { v -> onUpdate { it.copy(vitaminCMgPer100g = v) } })
                LabeledNumberField("Total carotenoids (optional)", "mg/100g", state.totalCarotenoidsMgPer100g, { v -> onUpdate { it.copy(totalCarotenoidsMgPer100g = v) } })
                LabeledTextField("Other laboratory parameters", state.otherLabParams, { v -> onUpdate { it.copy(otherLabParams = v) } }, singleLine = false)
            }
        }
        item {
            SectionCard(title = "Fruit Yield & Additional Parameters") {
                LabeledNumberField("Fruits per branch", null, state.fruitsPerBranch, { v -> onUpdate { it.copy(fruitsPerBranch = v) } })
                LabeledNumberField("Fruits per cluster", null, state.fruitsPerCluster, { v -> onUpdate { it.copy(fruitsPerCluster = v) } })
                LabeledNumberField("Estimated fruit yield per shrub", "kg", state.estimatedYieldKgPerShrub, { v -> onUpdate { it.copy(estimatedYieldKgPerShrub = v) } })
                LabeledNumberField("Fruit-bearing branch percentage", "%", state.fruitBearingBranchPercent, { v -> onUpdate { it.copy(fruitBearingBranchPercent = v) } })
                LabeledDropdown("Berry colour intensity", ColorIntensity.entries, state.berryColorIntensity, { it.label }, { v -> onUpdate { it.copy(berryColorIntensity = v) } })
                LabeledDropdown("Berry detachment ease", EaseLevel.entries, state.berryDetachmentEase, { it.label }, { v -> onUpdate { it.copy(berryDetachmentEase = v) } })
                LabeledNumberField("Fruit damage", "%", state.fruitDamagePercent, { v -> onUpdate { it.copy(fruitDamagePercent = v) } })
            }
        }
    }
}

@Composable
private fun StatSummaryRow(label: String, stats: com.kvk.leh.seabuckthorn.domain.calculations.StatSummary) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            if (stats.count == 0) "—" else
                "n=${stats.count}  mean ${"%.2f".format(stats.mean)}  min ${"%.2f".format(stats.min)}  max ${"%.2f".format(stats.max)}" +
                    (stats.standardDeviation?.let { "  sd ${"%.2f".format(it)}" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
