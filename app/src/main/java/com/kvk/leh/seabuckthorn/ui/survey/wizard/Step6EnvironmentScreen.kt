package com.kvk.leh.seabuckthorn.ui.survey.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kvk.leh.seabuckthorn.domain.model.*
import com.kvk.leh.seabuckthorn.ui.components.LabeledDropdown
import com.kvk.leh.seabuckthorn.ui.components.LabeledNumberField
import com.kvk.leh.seabuckthorn.ui.components.LabeledTextField
import com.kvk.leh.seabuckthorn.ui.components.SectionCard

@Composable
fun Step6EnvironmentScreen(state: SurveyFormState, onUpdate: ((SurveyFormState) -> SurveyFormState) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(title = "Terrain & Soil") {
                LabeledNumberField("Slope", "%", state.slopePercent, { v -> onUpdate { it.copy(slopePercent = v) } })
                LabeledDropdown("Aspect", Aspect.entries, state.aspect, { it.label }, { v -> onUpdate { it.copy(aspect = v) } })
                LabeledDropdown("Terrain type", TerrainType.entries, state.terrainType, { it.label }, { v -> onUpdate { it.copy(terrainType = v) } })
                LabeledTextField("Soil type / class (if known)", state.soilType, { v -> onUpdate { it.copy(soilType = v) } })
                LabeledTextField("Soil surface texture", state.soilSurfaceTexture, { v -> onUpdate { it.copy(soilSurfaceTexture = v) } })
                LabeledTextField("Soil surface condition", state.soilSurfaceCondition, { v -> onUpdate { it.copy(soilSurfaceCondition = v) } })
                LabeledDropdown("Soil moisture class", SoilMoistureClass.entries, state.soilMoistureClass, { it.label }, { v -> onUpdate { it.copy(soilMoistureClass = v) } })
            }
        }
        item {
            SectionCard(title = "Water & Irrigation") {
                LabeledDropdown("Irrigated / rainfed / natural", WaterRegime.entries, state.waterRegime, { it.label }, { v -> onUpdate { it.copy(waterRegime = v) } })
                LabeledTextField("River / stream name", state.riverName, { v -> onUpdate { it.copy(riverName = v) } })
                LabeledNumberField("Distance from river / water source", "m", state.distanceFromWaterM, { v -> onUpdate { it.copy(distanceFromWaterM = v) } })
            }
        }
        item {
            SectionCard(title = "Vegetation & Land Use") {
                LabeledTextField("Elevation zone", state.elevationZone, { v -> onUpdate { it.copy(elevationZone = v) } })
                LabeledTextField("Associated vegetation", state.associatedVegetation, { v -> onUpdate { it.copy(associatedVegetation = v) } }, singleLine = false)
                LabeledDropdown("Grazing intensity", Level4.entries, state.grazingIntensity, { it.label }, { v -> onUpdate { it.copy(grazingIntensity = v) } })
                LabeledTextField("Land-use history", state.landUseHistory, { v -> onUpdate { it.copy(landUseHistory = v) } }, singleLine = false)
            }
        }
    }
}
