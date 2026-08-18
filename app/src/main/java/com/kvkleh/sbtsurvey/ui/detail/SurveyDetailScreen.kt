package com.kvkleh.sbtsurvey.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kvkleh.sbtsurvey.data.local.SurveyEntity
import com.kvkleh.sbtsurvey.domain.LocationSource
import com.kvkleh.sbtsurvey.ui.appContainer
import com.kvkleh.sbtsurvey.ui.components.Fmt
import com.kvkleh.sbtsurvey.ui.components.InfoRow
import com.kvkleh.sbtsurvey.ui.components.SectionCard
import java.io.File

/** Read-only view of a saved survey, with a shortcut into the editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyDetailScreen(
    surveyRowId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val repository = appContainer().surveyRepository
    val survey by repository.observeById(surveyRowId)
        .collectAsStateWithLifecycle(initialValue = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = survey?.surveyId ?: "Survey",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(surveyRowId) },
                icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                text = { Text("Edit") }
            )
        }
    ) { padding ->
        val record = survey
        if (record == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { PhotoPanel(record) }

            item {
                SectionCard(title = "Survey", number = 1) {
                    Column {
                        InfoRow("Survey ID", record.surveyId)
                        InfoRow("Status", if (record.status == "draft") "Draft" else "Completed")
                        InfoRow("Date", Fmt.date(record.date))
                        InfoRow("Time", record.time)
                        InfoRow("Created", Fmt.dateTime(record.createdAt))
                        InfoRow("Last updated", Fmt.dateTime(record.updatedAt))
                    }
                }
            }

            item {
                SectionCard(title = "Surveyor") {
                    Column {
                        InfoRow("Name", record.surveyorName)
                        InfoRow("Designation", record.designation)
                        InfoRow("Organization", record.organization)
                    }
                }
            }

            item {
                SectionCard(title = "Location", number = 2) {
                    Column {
                        InfoRow("District", record.district)
                        InfoRow("Block", record.block)
                        InfoRow("Village", record.village)
                        InfoRow("Site", record.site)
                    }
                }
            }

            item {
                SectionCard(title = "GPS & Elevation", number = 4) {
                    Column {
                        InfoRow("Latitude", Fmt.coordinate(record.latitude))
                        InfoRow("Longitude", Fmt.coordinate(record.longitude))
                        InfoRow("Altitude", Fmt.metres(record.altitude))
                        InfoRow(
                            label = "Accuracy",
                            value = if (record.isManualLocation) {
                                "Not applicable"
                            } else {
                                Fmt.accuracy(record.gpsAccuracy)
                            }
                        )
                        InfoRow(
                            label = if (record.isManualLocation) "Entered at" else "Acquired at",
                            value = Fmt.dateTime(record.gpsTimestamp)
                        )
                        InfoRow(
                            label = "Source",
                            value = LocationSource.fromStorage(record.locationSource).label
                        )
                    }
                }
            }

            item {
                SectionCard(title = "Plant & Fruit") {
                    Column {
                        InfoRow("Shrub type", record.shrubType)
                        InfoRow("Plant height", Fmt.height(record))
                        InfoRow("Maturity stage", record.maturityStage)
                        InfoRow("Harvest date", Fmt.date(record.harvestDate))
                        InfoRow("Berry diameter", Fmt.decimal(record.berryDiameter, "mm"))
                        InfoRow("TSS", Fmt.decimal(record.tssBrix, "°Brix"))
                        InfoRow("Ease of harvest", record.easeOfHarvest)
                        InfoRow("Fruit shape", Fmt.fruitShape(record))
                    }
                }
            }

            item {
                SectionCard(title = "Photograph") {
                    Column {
                        InfoRow("File name", record.photoFileName)
                        InfoRow("Captured", Fmt.dateTime(record.photoCapturedAt))
                        InfoRow("Stored at", record.photoPath)
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun PhotoPanel(record: SurveyEntity) {
    val file = record.photoPath?.let { File(it) }
    if (file == null || !file.exists() || file.length() == 0L) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .aspectRatio(4f / 3f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(file)
                .crossfade(true)
                .build(),
            contentDescription = "Survey photograph",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
