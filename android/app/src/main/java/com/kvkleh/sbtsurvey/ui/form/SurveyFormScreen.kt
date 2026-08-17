package com.kvkleh.sbtsurvey.ui.form

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kvkleh.sbtsurvey.data.Formats
import com.kvkleh.sbtsurvey.data.SurveyField
import com.kvkleh.sbtsurvey.data.SurveyOptions
import com.kvkleh.sbtsurvey.data.db.SurveyEntity
import com.kvkleh.sbtsurvey.data.location.GpsStatus
import com.kvkleh.sbtsurvey.ui.components.AutoSaveIndicator
import com.kvkleh.sbtsurvey.ui.components.DetailRow
import com.kvkleh.sbtsurvey.ui.components.FormColumn
import com.kvkleh.sbtsurvey.ui.components.GpsStatusPill
import com.kvkleh.sbtsurvey.ui.components.SbtDropdownField
import com.kvkleh.sbtsurvey.ui.components.SbtRadioGroup
import com.kvkleh.sbtsurvey.ui.components.SbtSegmentedToggle
import com.kvkleh.sbtsurvey.ui.components.SbtTextField
import com.kvkleh.sbtsurvey.ui.components.SectionCard
import com.kvkleh.sbtsurvey.ui.components.rememberSurveyPhoto

/**
 * The numbered survey form. Everything typed here is written to the database
 * within a fraction of a second; the pill in the app bar reports the save state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyFormScreen(
    surveyRowId: Long,
    onReview: () -> Unit,
    onTakePhoto: () -> Unit,
    onBack: () -> Unit,
    viewModel: SurveyFormViewModel = viewModel()
) {
    LaunchedEffect(surveyRowId) {
        viewModel.load(surveyRowId)
        viewModel.startLocationUpdates()
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val survey = state.survey
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // Leaving the form for any reason — back, home, a phone call — writes the
    // current answers straight away instead of waiting for the debounce.
    DisposableEffect(Unit) {
        onDispose {
            viewModel.flushNow()
            viewModel.stopLocationUpdates()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Survey Form", style = MaterialTheme.typography.titleMedium)
                        if (survey != null) {
                            Text(
                                text = survey.surveyId,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.flushNow()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    AutoSaveIndicator(
                        state = state.autoSave,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data) }
        }
    ) { padding ->
        if (survey == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        FormColumn(modifier = Modifier.padding(padding)) {

            IdentitySection(survey)

            LocationSection(survey, state, viewModel)

            PhotoSection(survey, onTakePhoto, viewModel::clearPhoto)

            GpsSection(survey, state, viewModel)

            SectionCard(number = 5, title = "Shrub Type") {
                SbtRadioGroup(
                    options = SurveyOptions.shrubTypes,
                    selected = survey.shrubType,
                    onSelected = viewModel::setShrubType,
                    errorText = state.errorFor(SurveyField.SHRUB_TYPE)
                )
            }

            HeightSection(survey, state, viewModel)

            MaturitySection(survey, state, viewModel) { showDatePicker = true }

            SectionCard(
                number = 8,
                title = "Berry Characteristics",
                subtitle = "Measured on a representative sample of berries"
            ) {
                SbtTextField(
                    value = state.berryDiameterText,
                    onValueChange = viewModel::setBerryDiameterText,
                    label = "Berry Diameter (mm)",
                    keyboardType = KeyboardType.Decimal,
                    supportingText = "Decimals allowed, for example 6.5",
                    errorText = state.errorFor(SurveyField.BERRY_DIAMETER)
                )
                SbtTextField(
                    value = state.tssText,
                    onValueChange = viewModel::setTssText,
                    label = "TSS (°Brix)",
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                    supportingText = "Refractometer reading, for example 11.2",
                    errorText = state.errorFor(SurveyField.TSS)
                )
            }

            SectionCard(
                number = 9,
                title = "Fruit Shape Type",
                subtitle = "Dominant berry shape on this shrub"
            ) {
                SbtRadioGroup(
                    options = SurveyOptions.fruitShapes,
                    selected = survey.fruitShape,
                    onSelected = viewModel::setFruitShape
                )
            }

            SectionCard(number = 10, title = "Ease of Harvest") {
                SbtRadioGroup(
                    options = SurveyOptions.easeOfHarvest,
                    selected = survey.easeOfHarvest,
                    onSelected = viewModel::setEaseOfHarvest
                )
            }

            if (state.showErrors && state.errors.isNotEmpty()) {
                Text(
                    text = "${state.errors.size} field(s) still need attention before saving.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            Button(
                onClick = { if (viewModel.validateForReview()) onReview() },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Review & Save", style = MaterialTheme.typography.titleMedium)
            }

            Text(
                text = "Your entries are stored on this device as you type. " +
                    "Nothing is sent anywhere.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            Spacer(Modifier.height(12.dp))
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = Formats.localDateToPickerMillis(survey?.harvestDate)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setHarvestDate(
                        pickerState.selectedDateMillis?.let(Formats::pickerMillisToLocalDate)
                    )
                    showDatePicker = false
                }) { Text("Set date") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.setHarvestDate(null)
                    showDatePicker = false
                }) { Text("Clear") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun IdentitySection(survey: SurveyEntity) {
    SectionCard(
        number = 1,
        title = "Survey ID and Date",
        subtitle = "Generated automatically – no typing needed"
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                Text(
                    text = "Survey ID",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = survey.surveyId,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        DetailRow("Date", Formats.date(survey.createdAt))
        DetailRow("Time", Formats.time(survey.createdAt))
        DetailRow("Surveyor", survey.surveyorName)
    }
}

@Composable
private fun LocationSection(
    survey: SurveyEntity,
    state: SurveyFormUiState,
    viewModel: SurveyFormViewModel
) {
    val villageSuggestions = remember(survey.district, state.knownVillages) {
        (SurveyOptions.villagesFor(survey.district) + state.knownVillages)
            .distinct()
            .sorted()
    }
    SectionCard(
        number = 2,
        title = "Location Information",
        subtitle = "District and block are remembered for the next survey"
    ) {
        SbtDropdownField(
            value = survey.district,
            onValueChange = viewModel::setDistrict,
            label = "District",
            options = SurveyOptions.districts,
            required = true,
            errorText = state.errorFor(SurveyField.DISTRICT)
        )
        SbtDropdownField(
            value = survey.block,
            onValueChange = viewModel::setBlock,
            label = "Block",
            options = SurveyOptions.blocksFor(survey.district)
        )
        SbtDropdownField(
            value = survey.village,
            onValueChange = viewModel::setVillage,
            label = "Village",
            options = villageSuggestions,
            required = true,
            supportingText = "Type the name if it is not in the list",
            errorText = state.errorFor(SurveyField.VILLAGE)
        )
        SbtTextField(
            value = survey.site,
            onValueChange = viewModel::setSite,
            label = "Site",
            supportingText = "Plantation, orchard or landmark, e.g. 'Riverbank plantation'"
        )
    }
}

@Composable
private fun PhotoSection(
    survey: SurveyEntity,
    onTakePhoto: () -> Unit,
    onRemovePhoto: () -> Unit
) {
    val photo = rememberSurveyPhoto(survey.photoPath)
    SectionCard(
        number = 3,
        title = "Photo",
        subtitle = "Stored on the device as ${survey.surveyId}.jpg"
    ) {
        if (photo != null) {
            Image(
                bitmap = photo,
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onTakePhoto,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Retake")
                }
                OutlinedButton(
                    onClick = onRemovePhoto,
                    modifier = Modifier.heightIn(min = 52.dp)
                ) {
                    Text("Remove")
                }
            }
        } else {
            Button(
                onClick = onTakePhoto,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Take Photo")
            }
            Text(
                text = if (survey.photoFileName.isNullOrBlank()) {
                    "No photo yet. A photo is recommended but not required to save."
                } else {
                    "The photo file for this survey is missing from the device."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GpsSection(
    survey: SurveyEntity,
    state: SurveyFormUiState,
    viewModel: SurveyFormViewModel
) {
    SectionCard(
        number = 4,
        title = "GPS Information",
        subtitle = "Captured automatically from the device – no internet needed",
        trailing = { GpsStatusPill(state.gpsStatus) }
    ) {
        DetailRow("Latitude", Formats.coordinate(survey.latitude), emphasise = true)
        DetailRow("Longitude", Formats.coordinate(survey.longitude), emphasise = true)
        DetailRow(
            label = "Altitude",
            value = survey.altitude?.let { "${Formats.metres(it)} m" }.orEmpty(),
            emphasise = true
        )
        if (survey.accuracyM != null) {
            DetailRow("Accuracy", "± ${Formats.metres(survey.accuracyM)} m")
        }
        if (survey.locationCapturedAt != null) {
            DetailRow("Fix taken", Formats.dateTime(survey.locationCapturedAt))
        }

        when (val status = state.gpsStatus) {
            is GpsStatus.Ready -> if (!survey.hasLocation) {
                Text(
                    text = "A fix is available (± ${Formats.metres(status.fix.accuracyM)} m). " +
                        "Tap below to record it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            GpsStatus.PermissionRequired -> Text(
                text = "Allow location access to record coordinates. " +
                    "The survey can still be saved and the position added later.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )

            GpsStatus.ServicesDisabled -> Text(
                text = "Location services are switched off. Turn on GPS, then tap " +
                    "Update Location. The survey can be saved without coordinates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )

            GpsStatus.Unavailable -> Text(
                text = "No position after searching. Move to open sky, away from " +
                    "buildings, and tap Update Location. Coordinates can also be added " +
                    "later from the saved record.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )

            GpsStatus.NoReceiver -> Text(
                text = "This device has no satellite receiver — common on Wi-Fi-only " +
                    "tablets. Record the survey on a phone if coordinates are needed, " +
                    "or enter them later from the saved record.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )

            is GpsStatus.Acquiring -> Text(
                text = if (status.elapsedSeconds < 5) {
                    "Searching for satellites…"
                } else {
                    "Searching for satellites… ${status.elapsedSeconds}s. A cold start " +
                        "outdoors usually takes 30–60 seconds."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (survey.hasLocation && (survey.accuracyM ?: 0.0) > 100.0) {
            Text(
                text = "This position is approximate (± ${Formats.metres(survey.accuracyM)} m), " +
                    "probably from the network rather than satellites. Tap Update Location " +
                    "outdoors for a precise reading.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        OutlinedButton(
            onClick = viewModel::captureLocationNow,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(if (survey.hasLocation) "Update Location" else "Record Location")
        }
    }
}

@Composable
private fun HeightSection(
    survey: SurveyEntity,
    state: SurveyFormUiState,
    viewModel: SurveyFormViewModel
) {
    SectionCard(number = 6, title = "Plant Height") {
        SbtTextField(
            value = state.heightText,
            onValueChange = viewModel::setHeightText,
            label = "Plant Height",
            required = true,
            keyboardType = KeyboardType.Decimal,
            supportingText = "Height of the shrub in ${SurveyOptions.heightUnitLabel(survey.plantHeightUnit)}",
            errorText = state.errorFor(SurveyField.PLANT_HEIGHT)
        )
        Text(
            text = "Unit",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SbtSegmentedToggle(
            options = SurveyOptions.heightUnits,
            labels = listOf("metres (m)", "feet (ft)"),
            selected = survey.plantHeightUnit,
            onSelected = viewModel::setHeightUnit
        )
    }
}

@Composable
private fun MaturitySection(
    survey: SurveyEntity,
    state: SurveyFormUiState,
    viewModel: SurveyFormViewModel,
    onPickDate: () -> Unit
) {
    SectionCard(number = 7, title = "Dominant Fruit Maturity Stage") {
        SbtRadioGroup(
            options = SurveyOptions.maturityStages,
            selected = survey.maturityStage,
            onSelected = viewModel::setMaturityStage,
            errorText = state.errorFor(SurveyField.MATURITY_STAGE)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Harvest Date",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = onPickDate,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
        ) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(
                text = survey.harvestDate?.let(Formats::date) ?: "Select harvest date",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
