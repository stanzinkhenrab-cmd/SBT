package com.kvk.leh.seabuckthorn.ui.survey.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kvk.leh.seabuckthorn.data.local.entity.SurveyEntity
import com.kvk.leh.seabuckthorn.domain.model.GpsStatus
import com.kvk.leh.seabuckthorn.domain.model.MaturityStage
import com.kvk.leh.seabuckthorn.ui.components.ConfirmDialog
import com.kvk.leh.seabuckthorn.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyListScreen(
    viewModel: SurveyListViewModel,
    onOpenSurvey: (String) -> Unit,
    onBack: () -> Unit
) {
    val filters by viewModel.filters.collectAsState()
    val surveys by viewModel.surveys.collectAsState()
    val villages by viewModel.villages.collectAsState()
    val surveyors by viewModel.surveyors.collectAsState()
    var surveyPendingDelete by remember { mutableStateOf<SurveyEntity?>(null) }
    var showFilters by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Surveys (${surveys.size})") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(Icons.Filled.FilterAlt, contentDescription = "Filters")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = filters.query,
                onValueChange = viewModel::updateQuery,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search survey ID, village, site, surveyor…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )

            if (showFilters) {
                Column(modifier = Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Maturity stage", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        FilterChip(selected = filters.maturityStage == null, onClick = { viewModel.updateMaturityStage(null) }, label = { Text("All") })
                        MaturityStage.entries.forEach { stage ->
                            FilterChip(
                                selected = filters.maturityStage == stage,
                                onClick = { viewModel.updateMaturityStage(stage) },
                                label = { Text(stage.label) }
                            )
                        }
                    }
                    if (villages.isNotEmpty()) {
                        Text("Village", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            FilterChip(selected = filters.village.isEmpty(), onClick = { viewModel.updateVillage("") }, label = { Text("All") })
                            villages.forEach { v ->
                                FilterChip(selected = filters.village == v, onClick = { viewModel.updateVillage(v) }, label = { Text(v) })
                            }
                        }
                    }
                    if (surveyors.isNotEmpty()) {
                        Text("Surveyor", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            FilterChip(selected = filters.surveyor.isEmpty(), onClick = { viewModel.updateSurveyor("") }, label = { Text("All") })
                            surveyors.forEach { s ->
                                FilterChip(selected = filters.surveyor == s, onClick = { viewModel.updateSurveyor(s) }, label = { Text(s) })
                            }
                        }
                    }
                }
            }

            if (surveys.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Text("No surveys match the current filters.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(surveys, key = { it.id }) { survey ->
                        SurveyListItem(
                            survey = survey,
                            onClick = { onOpenSurvey(survey.id) },
                            onDuplicate = { viewModel.duplicateSurvey(survey.id) { newId -> onOpenSurvey(newId) } },
                            onDelete = { surveyPendingDelete = survey }
                        )
                    }
                }
            }
        }
    }

    surveyPendingDelete?.let { survey ->
        ConfirmDialog(
            title = "Delete survey?",
            message = "${survey.surveyCode} and all its shrub, phenology, fruit and photo data will be permanently deleted from this device.",
            onConfirm = {
                viewModel.deleteSurvey(survey.id)
                surveyPendingDelete = null
            },
            onDismiss = { surveyPendingDelete = null }
        )
    }
}

@Composable
private fun SurveyListItem(survey: SurveyEntity, onClick: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Row {
                    Text(survey.surveyCode, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (survey.isDraft) {
                        Text("  DRAFT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
                Text(
                    listOf(survey.siteName, survey.village).filter { it.isNotBlank() }.joinToString(", ").ifBlank { "No location entered" },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "${DateUtils.epochDayToDisplay(survey.surveyDateEpochDay)} · ${survey.surveyorName.ifBlank { "Unknown surveyor" }}" +
                        if (survey.gpsStatus == GpsStatus.CAPTURED.name) " · GPS ✓" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                IconButton(onClick = onDuplicate) { Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
            }
        }
    }
}
