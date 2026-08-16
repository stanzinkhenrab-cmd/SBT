package com.kvk.leh.seabuckthorn.ui.survey.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kvk.leh.seabuckthorn.domain.model.LandUseType
import com.kvk.leh.seabuckthorn.domain.model.OwnershipStatus
import com.kvk.leh.seabuckthorn.ui.components.DatePickerField
import com.kvk.leh.seabuckthorn.ui.components.LabeledDropdown
import com.kvk.leh.seabuckthorn.ui.components.LabeledTextField
import com.kvk.leh.seabuckthorn.ui.components.ReadOnlyValueField
import com.kvk.leh.seabuckthorn.ui.components.SectionCard
import com.kvk.leh.seabuckthorn.ui.components.TimePickerField

@Composable
fun Step1SurveyInfoScreen(state: SurveyFormState, onUpdate: ((SurveyFormState) -> SurveyFormState) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(title = "Survey Identity") {
                ReadOnlyValueField(label = "Survey ID", value = state.surveyCode)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DatePickerField(
                        label = "Date",
                        epochDay = state.surveyDateEpochDay,
                        onDateSelected = { day -> onUpdate { it.copy(surveyDateEpochDay = day) } },
                        modifier = Modifier.weight(1f)
                    )
                    TimePickerField(
                        label = "Time",
                        time = state.surveyTime,
                        onTimeSelected = { time -> onUpdate { it.copy(surveyTime = time) } },
                        modifier = Modifier.weight(1f)
                    )
                }
                LabeledTextField(
                    label = "Surveyor name",
                    value = state.surveyorName,
                    onValueChange = { v -> onUpdate { it.copy(surveyorName = v) } }
                )
            }
        }
        item {
            SectionCard(title = "Site Location") {
                LabeledTextField(label = "District", value = state.district, onValueChange = { v -> onUpdate { it.copy(district = v) } })
                LabeledTextField(label = "Block", value = state.block, onValueChange = { v -> onUpdate { it.copy(block = v) } })
                LabeledTextField(label = "Village / Location", value = state.village, onValueChange = { v -> onUpdate { it.copy(village = v) } })
                LabeledTextField(label = "Site / Field name", value = state.siteName, onValueChange = { v -> onUpdate { it.copy(siteName = v) } })
            }
        }
        item {
            SectionCard(title = "Land Use & Ownership") {
                LabeledDropdown(
                    label = "Land-use type",
                    options = LandUseType.entries,
                    selected = state.landUseType,
                    optionLabel = { it.label },
                    onSelected = { v -> onUpdate { it.copy(landUseType = v) } }
                )
                if (state.landUseType == LandUseType.OTHER) {
                    LabeledTextField(label = "Specify land-use type", value = state.landUseOther, onValueChange = { v -> onUpdate { it.copy(landUseOther = v) } })
                }
                LabeledDropdown(
                    label = "Ownership / land status",
                    options = OwnershipStatus.entries,
                    selected = state.ownershipStatus,
                    optionLabel = { it.label },
                    onSelected = { v -> onUpdate { it.copy(ownershipStatus = v) } }
                )
            }
        }
        item {
            SectionCard(title = "Remarks") {
                LabeledTextField(
                    label = "Optional remarks",
                    value = state.remarks,
                    onValueChange = { v -> onUpdate { it.copy(remarks = v) } },
                    singleLine = false
                )
            }
        }
    }
}
