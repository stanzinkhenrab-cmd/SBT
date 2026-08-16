package com.kvkleh.sbtsurvey.ui.review

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.kvkleh.sbtsurvey.data.Formats
import com.kvkleh.sbtsurvey.data.db.SurveyEntity
import com.kvkleh.sbtsurvey.ui.components.DetailRow
import com.kvkleh.sbtsurvey.ui.components.SectionCard
import com.kvkleh.sbtsurvey.ui.components.rememberSurveyPhoto

/**
 * The read-only view of a record, shared by the review step and the saved-survey
 * detail screen so that both always show exactly the same fields.
 */
@Composable
fun SurveySummary(survey: SurveyEntity, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SectionCard(
            number = null,
            icon = Icons.Filled.Person,
            title = "Surveyor",
            modifier = Modifier.padding(bottom = 14.dp)
        ) {
            DetailRow("Survey ID", survey.surveyId, emphasise = true)
            DetailRow("Date", Formats.date(survey.createdAt))
            DetailRow("Time", Formats.time(survey.createdAt))
            DetailRow("Surveyor Name", survey.surveyorName)
            DetailRow("Designation", survey.designation)
            DetailRow("Organization", survey.organization)
        }

        SectionCard(
            number = null,
            icon = Icons.Filled.LocationOn,
            title = "Location",
            modifier = Modifier.padding(bottom = 14.dp)
        ) {
            DetailRow("District", survey.district)
            DetailRow("Block", survey.block)
            DetailRow("Village", survey.village)
            DetailRow("Site", survey.site)
            DetailRow("Latitude", Formats.coordinate(survey.latitude))
            DetailRow("Longitude", Formats.coordinate(survey.longitude))
            DetailRow(
                "Altitude",
                survey.altitude?.let { "${Formats.metres(it)} m" }.orEmpty()
            )
            if (survey.accuracyM != null) {
                DetailRow("GPS accuracy", "± ${Formats.metres(survey.accuracyM)} m")
            }
        }

        SectionCard(
            number = null,
            icon = Icons.Filled.PhotoCamera,
            title = "Photo",
            modifier = Modifier.padding(bottom = 14.dp)
        ) {
            val photo by rememberSurveyPhoto(survey.photoPath)
            if (photo != null) {
                Image(
                    bitmap = photo!!,
                    contentDescription = "Photograph for ${survey.surveyId}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(12.dp)
                        )
                )
            }
            DetailRow("Photo file", survey.photoFileName.orEmpty())
        }

        SectionCard(
            number = null,
            icon = Icons.Filled.Landscape,
            title = "Plant",
            modifier = Modifier.padding(bottom = 14.dp)
        ) {
            DetailRow("Shrub Type", survey.shrubType.orEmpty())
            DetailRow(
                "Plant Height",
                survey.plantHeight?.let {
                    "${Formats.number(it)} ${survey.plantHeightUnit}"
                }.orEmpty()
            )
            DetailRow("Dominant Fruit Maturity Stage", survey.maturityStage.orEmpty())
            DetailRow("Harvest Date", Formats.date(survey.harvestDate))
        }

        SectionCard(
            number = null,
            icon = Icons.Filled.Science,
            title = "Berries and harvest"
        ) {
            DetailRow(
                "Berry Diameter",
                survey.berryDiameterMm?.let { "${Formats.number(it)} mm" }.orEmpty()
            )
            DetailRow(
                "TSS",
                survey.tssBrix?.let { "${Formats.number(it)} °Brix" }.orEmpty()
            )
            DetailRow("Ease of Harvest", survey.easeOfHarvest.orEmpty())
        }
    }
}

/** Compact one-line description used in lists and map callouts. */
fun surveySubtitle(survey: SurveyEntity): String = buildString {
    append(Formats.dateTime(survey.createdAt))
    if (survey.village.isNotBlank()) append(" · ${survey.village}")
    if (!survey.maturityStage.isNullOrBlank()) append(" · ${survey.maturityStage}")
}

@Composable
fun MissingDataNotice(warnings: List<String>, modifier: Modifier = Modifier) {
    if (warnings.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.tertiaryContainer,
                RoundedCornerShape(14.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = "Optional information not recorded",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
        warnings.forEach { warning ->
            Text(
                text = "• $warning",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Text(
            text = "The survey can still be saved. These fields can be filled in later " +
                "by editing the record.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
