package com.kvk.leh.seabuckthorn.ui.survey.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kvk.leh.seabuckthorn.domain.calculations.DescriptiveStatistics
import com.kvk.leh.seabuckthorn.domain.model.MaturityStage
import com.kvk.leh.seabuckthorn.ui.components.ConfirmDialog
import com.kvk.leh.seabuckthorn.ui.components.InfoRow
import com.kvk.leh.seabuckthorn.ui.components.PhotoThumbnail
import com.kvk.leh.seabuckthorn.ui.components.SectionCard
import com.kvk.leh.seabuckthorn.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyDetailScreen(
    viewModel: SurveyDetailViewModel,
    surveyId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDeleted: () -> Unit
) {
    val record by viewModel.record.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(surveyId) { viewModel.load(surveyId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(record?.survey?.surveyCode ?: "Survey") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Edit") },
                icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = { onEdit(surveyId) }
            )
        }
    ) { padding ->
        val r = record
        if (r == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val s = r.survey
        val sh = r.shrub
        val ph = r.phenology
        val fq = r.fruitQuality
        val env = r.environment
        val lengths = r.berries.mapNotNull { it.lengthMm }
        val widths = r.berries.mapNotNull { it.widthMm }
        val weights = r.berries.mapNotNull { it.weightG }
        val lengthStats = DescriptiveStatistics.summarize(lengths)
        val widthStats = DescriptiveStatistics.summarize(widths)
        val weightStats = DescriptiveStatistics.summarize(weights)

        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionCard(title = "Survey Information") {
                    InfoRow("Date", DateUtils.epochDayToDisplay(s.surveyDateEpochDay))
                    InfoRow("Time", s.surveyTime)
                    InfoRow("Surveyor", s.surveyorName)
                    InfoRow("District / Block", "${s.district} / ${s.block}")
                    InfoRow("Village", s.village)
                    InfoRow("Site", s.siteName)
                    InfoRow("Land use", s.landUseType)
                    InfoRow("Ownership", s.ownershipStatus)
                    if (!s.remarks.isNullOrBlank()) InfoRow("Remarks", s.remarks)
                }
            }
            item {
                SectionCard(title = "GPS Location") {
                    InfoRow("Status", s.gpsStatus)
                    InfoRow("Latitude", s.latitude?.let { "%.6f°".format(it) } ?: "Unavailable")
                    InfoRow("Longitude", s.longitude?.let { "%.6f°".format(it) } ?: "Unavailable")
                    InfoRow("Altitude", s.altitude?.let { "%.1f m".format(it) } ?: "Unavailable")
                    InfoRow("Accuracy", s.gpsAccuracyM?.let { "±%.1f m".format(it) } ?: "—")
                }
            }
            if (sh != null) {
                item {
                    SectionCard(title = "Shrub Characteristics") {
                        InfoRow("Type / growth form", "${sh.shrubType} / ${sh.growthForm}")
                        InfoRow("Height", sh.plantHeightM?.let { "%.2f m".format(it) } ?: "—")
                        InfoRow("Canopy N-S / E-W", "${sh.canopyNsM ?: "—"} / ${sh.canopyEwM ?: "—"} m")
                        InfoRow("Avg. canopy diameter", sh.avgCanopyDiameterM?.let { "%.2f m".format(it) } ?: "—")
                        InfoRow("Canopy area", sh.canopyAreaM2?.let { "%.2f m²".format(it) } ?: "—")
                        InfoRow("Estimated age", sh.estimatedAgeYears?.let { "%.0f years".format(it) } ?: "—")
                        InfoRow("Regeneration status", sh.regenerationStatus ?: "—")
                        InfoRow("Overall health", sh.overallHealth ?: "—")
                    }
                }
            }
            if (ph != null) {
                item {
                    SectionCard(title = "Phenology & Maturity") {
                        InfoRow("Dominant stage", ph.dominantMaturityStage?.let {
                            runCatching { MaturityStage.valueOf(it).label }.getOrDefault(it)
                        } ?: "—")
                        InfoRow("Unripe / Intermediate / Ripe / Overripe", "${ph.pctUnripe ?: 0}% / ${ph.pctIntermediate ?: 0}% / ${ph.pctRipe ?: 0}% / ${ph.pctOverripe ?: 0}%")
                        InfoRow("50% maturity date", DateUtils.epochMillisToDisplayDate(ph.fiftyPercentMaturityDate))
                        InfoRow("Peak maturity date", DateUtils.epochMillisToDisplayDate(ph.peakMaturityDate))
                    }
                }
            }
            item {
                SectionCard(title = "Berry Measurements") {
                    InfoRow("Berries measured", r.berries.size.toString())
                    InfoRow("Mean length", lengthStats.mean?.let { "%.2f mm".format(it) } ?: "—")
                    InfoRow("Mean diameter", widthStats.mean?.let { "%.2f mm".format(it) } ?: "—")
                    InfoRow("Mean weight", weightStats.mean?.let { "%.2f g".format(it) } ?: "—")
                }
            }
            if (fq != null) {
                item {
                    SectionCard(title = "Fruit Quality") {
                        InfoRow("Colour / shape / firmness", "${fq.fruitColor} / ${fq.fruitShape} / ${fq.fruitFirmness}")
                        InfoRow("TSS", fq.tssBrix?.let { "%.1f °Brix".format(it) } ?: "—")
                        InfoRow("pH", fq.ph?.let { "%.2f".format(it) } ?: "—")
                        InfoRow("Juice yield", fq.juiceYieldPercent?.let { "%.1f%%".format(it) } ?: "—")
                        InfoRow("Estimated yield/shrub", fq.estimatedYieldKgPerShrub?.let { "%.2f kg".format(it) } ?: "—")
                    }
                }
            }
            if (env != null) {
                item {
                    SectionCard(title = "Environment") {
                        InfoRow("Slope / aspect", "${env.slopePercent ?: "—"}% / ${env.aspect ?: "—"}")
                        InfoRow("Terrain / soil", "${env.terrainType ?: "—"} / ${env.soilType.orEmpty().ifBlank { "—" }}")
                        InfoRow("Water regime", env.waterRegime ?: "—")
                        InfoRow("River / distance", "${env.riverName.orEmpty().ifBlank { "—" }} / ${env.distanceFromWaterM?.let { "%.0f m".format(it) } ?: "—"}")
                    }
                }
            }
            item {
                SectionCard(title = "Photographs (${r.photos.size})") {
                    if (r.photos.isEmpty()) {
                        Text("No photographs recorded.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        val rowCount = (r.photos.size + 2) / 3
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().height((rowCount * 120).dp)
                        ) {
                            items(r.photos, key = { it.id }) { photo ->
                                Card(modifier = Modifier.aspectRatio(1f)) {
                                    PhotoThumbnail(filePath = photo.filePath, modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Delete this survey?",
            message = "This will permanently delete ${record?.survey?.surveyCode} and all related data and photos from this device.",
            onConfirm = {
                showDeleteConfirm = false
                viewModel.delete(surveyId, onDeleted)
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
