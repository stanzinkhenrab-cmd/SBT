package com.kvk.leh.seabuckthorn.ui.survey.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kvk.leh.seabuckthorn.domain.model.MaturityStage
import com.kvk.leh.seabuckthorn.ui.components.LabeledNumberField
import com.kvk.leh.seabuckthorn.ui.components.MaturityStageCard
import com.kvk.leh.seabuckthorn.ui.components.OptionalDatePickerField
import com.kvk.leh.seabuckthorn.ui.components.SectionCard
import com.kvk.leh.seabuckthorn.ui.theme.ErrorRed40

@Composable
fun Step4PhenologyScreen(state: SurveyFormState, onUpdate: ((SurveyFormState) -> SurveyFormState) -> Unit, modifier: Modifier = Modifier) {
    val total = state.maturityPercentTotal
    val hasAnyPercent = listOfNotNull(state.pctUnripe, state.pctIntermediate, state.pctRipe, state.pctOverripe).isNotEmpty()
    val totalIsValid = !hasAnyPercent || kotlin.math.abs(total - 100.0) <= 1.0

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(title = "Dominant Fruit Maturity Stage") {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(MaturityStage.entries) { stage ->
                        MaturityStageCard(
                            stage = stage,
                            selected = state.dominantMaturityStage == stage,
                            onClick = { onUpdate { it.copy(dominantMaturityStage = stage) } }
                        )
                    }
                }
            }
        }
        item {
            SectionCard(
                title = "Maturity Percentage Breakdown",
                subtitle = "Estimated proportion of fruit at each stage. Should total approximately 100%."
            ) {
                LabeledNumberField("% Unripe", "%", state.pctUnripe, { v -> onUpdate { it.copy(pctUnripe = v) } })
                LabeledNumberField("% Intermediate", "%", state.pctIntermediate, { v -> onUpdate { it.copy(pctIntermediate = v) } })
                LabeledNumberField("% Ripe", "%", state.pctRipe, { v -> onUpdate { it.copy(pctRipe = v) } })
                LabeledNumberField("% Overripe", "%", state.pctOverripe, { v -> onUpdate { it.copy(pctOverripe = v) } })
                Text(
                    text = "Total: ${"%.1f".format(total)}%" + if (!totalIsValid) "  — expected ~100%" else "",
                    color = if (totalIsValid) MaterialTheme.colorScheme.onSurfaceVariant else ErrorRed40,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        item {
            SectionCard(
                title = "Phenological Dates",
                subtitle = "Mark each date as observed in the field, or estimated if you weren't present at that exact stage."
            ) {
                PhenoDateRow("Flowering initiation", state.floweringInitiationDate) { v -> onUpdate { it.copy(floweringInitiationDate = v) } }
                PhenoDateRow("Flowering peak", state.floweringPeakDate) { v -> onUpdate { it.copy(floweringPeakDate = v) } }
                PhenoDateRow("Fruit set", state.fruitSetDate) { v -> onUpdate { it.copy(fruitSetDate = v) } }
                PhenoDateRow("Fruit development initiation", state.fruitDevelopmentInitiationDate) { v -> onUpdate { it.copy(fruitDevelopmentInitiationDate = v) } }
                PhenoDateRow("First fruit colour change", state.firstColorChangeDate) { v -> onUpdate { it.copy(firstColorChangeDate = v) } }
                PhenoDateRow("First maturity", state.firstMaturityDate) { v -> onUpdate { it.copy(firstMaturityDate = v) } }
                PhenoDateRow("50% maturity", state.fiftyPercentMaturityDate) { v -> onUpdate { it.copy(fiftyPercentMaturityDate = v) } }
                PhenoDateRow("Peak maturity", state.peakMaturityDate) { v -> onUpdate { it.copy(peakMaturityDate = v) } }
                PhenoDateRow("Harvest initiation", state.harvestInitiationDate) { v -> onUpdate { it.copy(harvestInitiationDate = v) } }
                PhenoDateRow("Estimated full maturity", state.estimatedFullMaturityDate) { v -> onUpdate { it.copy(estimatedFullMaturityDate = v) } }
            }
        }
    }
}

@Composable
private fun PhenoDateRow(label: String, value: PhenoDate, onChange: (PhenoDate) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OptionalDatePickerField(
            label = label,
            epochMillis = value.epochMillis,
            onDateSelected = { millis -> onChange(value.copy(epochMillis = millis)) }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !value.isEstimated, onClick = { onChange(value.copy(isEstimated = false)) }, label = { Text("Date observed") })
            FilterChip(selected = value.isEstimated, onClick = { onChange(value.copy(isEstimated = true)) }, label = { Text("Date estimated") })
        }
    }
}
