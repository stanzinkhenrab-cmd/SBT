package com.kvk.leh.seabuckthorn.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kvk.leh.seabuckthorn.ui.components.SectionCard
import com.kvk.leh.seabuckthorn.ui.components.SimpleBarChart
import com.kvk.leh.seabuckthorn.ui.components.StatTile
import com.kvk.leh.seabuckthorn.ui.components.colorForStage
import com.kvk.leh.seabuckthorn.util.DateUtils
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNewSurvey: () -> Unit,
    onOpenSurveyList: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val stats by viewModel.stats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Seabuckthorn Field Survey", style = MaterialTheme.typography.titleLarge)
                        Text("Ladakh", style = MaterialTheme.typography.labelMedium)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSurveyList) {
                        Icon(Icons.Filled.Search, contentDescription = "Search surveys")
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Survey Map") }, leadingIcon = { Icon(Icons.Filled.Map, null) }, onClick = { menuExpanded = false; onOpenMap() })
                        DropdownMenuItem(text = { Text("Export Data") }, leadingIcon = { Icon(Icons.Filled.SaveAlt, null) }, onClick = { menuExpanded = false; onOpenExport() })
                        DropdownMenuItem(text = { Text("Backup & Restore") }, leadingIcon = { Icon(Icons.Filled.SettingsBackupRestore, null) }, onClick = { menuExpanded = false; onOpenBackup() })
                        DropdownMenuItem(text = { Text("About") }, leadingIcon = { Icon(Icons.Filled.Info, null) }, onClick = { menuExpanded = false; onOpenAbout() })
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("New Survey") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = onNewSurvey
            )
        }
    ) { padding ->
        if (isLoading || stats == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val s = stats!!
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        StatTile("Total Surveys", s.totalSurveys.toString(), modifier = Modifier.weight(1f))
                        StatTile("Total Shrubs", s.totalShrubs.toString(), modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        StatTile("Villages Surveyed", s.villagesSurveyed.toString(), modifier = Modifier.weight(1f))
                        StatTile("Avg. TSS", s.averageTssBrix?.let { "${(it * 10).roundToInt() / 10.0} °Bx" } ?: "—", modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        StatTile("Avg. Berry Length", s.averageBerryLengthMm?.let { "${(it * 10).roundToInt() / 10.0} mm" } ?: "—", modifier = Modifier.weight(1f))
                        StatTile(
                            "Latest Survey",
                            s.latestSurvey?.let { DateUtils.epochDayToDisplay(it.surveyDateEpochDay) } ?: "—",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile("Ripe", s.ripeObservations.toString(), modifier = Modifier.weight(1f), accentColor = colorForStage(com.kvk.leh.seabuckthorn.domain.model.MaturityStage.RIPE))
                    StatTile("Intermediate", s.intermediateObservations.toString(), modifier = Modifier.weight(1f), accentColor = colorForStage(com.kvk.leh.seabuckthorn.domain.model.MaturityStage.INTERMEDIATE))
                    StatTile("Unripe", s.unripeObservations.toString(), modifier = Modifier.weight(1f), accentColor = colorForStage(com.kvk.leh.seabuckthorn.domain.model.MaturityStage.UNRIPE))
                }
            }

            item {
                SectionCard(title = "Maturity Stage Distribution") {
                    SimpleBarChart(data = s.maturityDistribution.map { it.key.label to it.value })
                }
            }

            item {
                SectionCard(title = "TSS Distribution (°Brix)") {
                    SimpleBarChart(data = s.tssDistributionBuckets.toList(), barColor = MaterialTheme.colorScheme.secondary)
                }
            }

            item {
                SectionCard(title = "Berry Size Distribution (length)") {
                    SimpleBarChart(data = s.berrySizeDistributionBuckets.toList(), barColor = MaterialTheme.colorScheme.tertiary)
                }
            }

            if (s.surveysByVillage.isNotEmpty()) {
                item {
                    SectionCard(title = "Surveys by Village") {
                        SimpleBarChart(data = s.surveysByVillage.take(6).map { it.first to it.second })
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("100% Offline", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            "All figures above are computed entirely from data stored on this device. No internet connection is used or required.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}
