package com.kvk.leh.seabuckthorn.ui.survey.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kvk.leh.seabuckthorn.domain.calculations.CanopyCalculations
import com.kvk.leh.seabuckthorn.domain.model.*
import com.kvk.leh.seabuckthorn.ui.components.LabeledDropdown
import com.kvk.leh.seabuckthorn.ui.components.LabeledNumberField
import com.kvk.leh.seabuckthorn.ui.components.LabeledTextField
import com.kvk.leh.seabuckthorn.ui.components.ReadOnlyValueField
import com.kvk.leh.seabuckthorn.ui.components.SectionCard

@Composable
fun Step3ShrubScreen(state: SurveyFormState, onUpdate: ((SurveyFormState) -> SurveyFormState) -> Unit, modifier: Modifier = Modifier) {
    val avgDiameter = CanopyCalculations.averageCanopyDiameter(state.canopyNsM, state.canopyEwM)
    val canopyArea = CanopyCalculations.canopyArea(avgDiameter)

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(title = "Shrub Type & Form") {
                LabeledDropdown("Shrub type", ShrubType.entries, state.shrubType, { it.label }, { v -> onUpdate { it.copy(shrubType = v) } })
                LabeledDropdown("Growth form", GrowthForm.entries, state.growthForm, { it.label }, { v -> onUpdate { it.copy(growthForm = v) } })
            }
        }
        item {
            SectionCard(title = "Shrub Size", subtitle = "Average diameter and canopy area are calculated automatically.") {
                LabeledNumberField("Plant height", "m", state.plantHeightM, { v -> onUpdate { it.copy(plantHeightM = v) } })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledNumberField("Canopy N-S", "m", state.canopyNsM, { v -> onUpdate { it.copy(canopyNsM = v) } }, modifier = Modifier.weight(1f))
                    LabeledNumberField("Canopy E-W", "m", state.canopyEwM, { v -> onUpdate { it.copy(canopyEwM = v) } }, modifier = Modifier.weight(1f))
                }
                ReadOnlyValueField("Average canopy diameter (m)", avgDiameter?.let { "%.2f".format(it) } ?: "—")
                ReadOnlyValueField("Estimated canopy area (m²)", canopyArea?.let { "%.2f".format(it) } ?: "—")
                LabeledNumberField("Stem/trunk circumference", "cm", state.stemCircumferenceCm, { v -> onUpdate { it.copy(stemCircumferenceCm = v) } })
                LabeledNumberField("Number of major stems", null, state.numMajorStems, { v -> onUpdate { it.copy(numMajorStems = v) } }, allowDecimal = false)
            }
        }
        item {
            SectionCard(title = "Plant Age") {
                LabeledNumberField("Estimated age", "years", state.estimatedAgeYears, { v -> onUpdate { it.copy(estimatedAgeYears = v) } })
                LabeledTextField("Age estimation method / remarks", state.ageEstimationMethod, { v -> onUpdate { it.copy(ageEstimationMethod = v) } }, singleLine = false)
            }
        }
        item {
            SectionCard(title = "Stand Characteristics") {
                LabeledNumberField("Plant density", "per ha", state.plantDensityPerHa, { v -> onUpdate { it.copy(plantDensityPerHa = v) } })
                LabeledNumberField("Approximate spacing", "m", state.approxSpacingM, { v -> onUpdate { it.copy(approxSpacingM = v) } })
                LabeledDropdown("Regeneration status", RegenerationStatus.entries, state.regenerationStatus, { it.label }, { v -> onUpdate { it.copy(regenerationStatus = v) } })
                CheckboxRow("Presence of suckers", state.presenceOfSuckers) { v -> onUpdate { it.copy(presenceOfSuckers = v) } }
                CheckboxRow("Flowering observed", state.floweringStatus) { v -> onUpdate { it.copy(floweringStatus = v) } }
                CheckboxRow("Fruiting observed", state.fruitingStatus) { v -> onUpdate { it.copy(fruitingStatus = v) } }
            }
        }
        item {
            SectionCard(title = "Additional Vegetative Parameters") {
                LabeledNumberField("Number of branches", null, state.numBranches, { v -> onUpdate { it.copy(numBranches = v) } }, allowDecimal = false)
                LabeledNumberField("Main stem diameter", "cm", state.mainStemDiameterCm, { v -> onUpdate { it.copy(mainStemDiameterCm = v) } })
                LabeledNumberField("Branch diameter", "cm", state.branchDiameterCm, { v -> onUpdate { it.copy(branchDiameterCm = v) } })
                LabeledTextField("Leaf colour", state.leafColor, { v -> onUpdate { it.copy(leafColor = v) } })
                LabeledDropdown("Leaf density", Level4.entries, state.leafDensity, { it.label }, { v -> onUpdate { it.copy(leafDensity = v) } })
                LabeledNumberField("Approximate canopy cover", "%", state.canopyCoverPercent, { v -> onUpdate { it.copy(canopyCoverPercent = v) } })
                LabeledDropdown("Thorn density", Level4.entries, state.thornDensity, { it.label }, { v -> onUpdate { it.copy(thornDensity = v) } })
                LabeledDropdown("Sucker abundance", Level4.entries, state.suckerAbundance, { it.label }, { v -> onUpdate { it.copy(suckerAbundance = v) } })
            }
        }
        item {
            SectionCard(title = "Health Parameters") {
                LabeledDropdown("Overall plant health", HealthStatus.entries, state.overallHealth, { it.label }, { v -> onUpdate { it.copy(overallHealth = v) } })
                LabeledDropdown("Pest incidence", Level4.entries, state.pestIncidence, { it.label }, { v -> onUpdate { it.copy(pestIncidence = v) } })
                LabeledDropdown("Disease incidence", Level4.entries, state.diseaseIncidence, { it.label }, { v -> onUpdate { it.copy(diseaseIncidence = v) } })
                LabeledDropdown("Browsing / grazing damage", Level4.entries, state.browsingDamage, { it.label }, { v -> onUpdate { it.copy(browsingDamage = v) } })
                LabeledDropdown("Mechanical damage", Level4.entries, state.mechanicalDamage, { it.label }, { v -> onUpdate { it.copy(mechanicalDamage = v) } })
                LabeledDropdown("Drought stress", Level4.entries, state.droughtStress, { it.label }, { v -> onUpdate { it.copy(droughtStress = v) } })
                LabeledTextField("Other stress symptoms", state.otherStressSymptoms, { v -> onUpdate { it.copy(otherStressSymptoms = v) } }, singleLine = false)
            }
        }
    }
}

@Composable
private fun CheckboxRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
