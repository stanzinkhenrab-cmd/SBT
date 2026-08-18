package com.kvkleh.sbtsurvey.ui.survey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvkleh.sbtsurvey.data.local.SurveyEntity
import com.kvkleh.sbtsurvey.domain.EaseOfHarvest
import com.kvkleh.sbtsurvey.domain.FruitShape
import com.kvkleh.sbtsurvey.domain.HeightUnit
import com.kvkleh.sbtsurvey.domain.LadakhAdmin
import com.kvkleh.sbtsurvey.domain.MaturityStage
import com.kvkleh.sbtsurvey.domain.ShrubType
import com.kvkleh.sbtsurvey.domain.toFeet
import com.kvkleh.sbtsurvey.domain.toMetres
import com.kvkleh.sbtsurvey.ui.appContainer
import com.kvkleh.sbtsurvey.ui.components.Fmt
import com.kvkleh.sbtsurvey.ui.components.OptionChips
import com.kvkleh.sbtsurvey.ui.components.SbtDateField
import com.kvkleh.sbtsurvey.ui.components.SbtDropdownField
import com.kvkleh.sbtsurvey.ui.components.SbtNumberField
import com.kvkleh.sbtsurvey.ui.components.SbtTextField
import com.kvkleh.sbtsurvey.ui.components.SectionCard
import com.kvkleh.sbtsurvey.ui.components.UnitSelector
import com.kvkleh.sbtsurvey.ui.surveyFormViewModel
import java.util.Locale

/**
 * The survey form.
 *
 * Every field writes through to the database as it is edited, so the surveyor never has
 * to think about saving. Save & Finish only validates and marks the record complete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyFormScreen(
    surveyRowId: Long,
    onBack: () -> Unit,
    onFinished: (Long) -> Unit
) {
    val viewModel = surveyFormViewModel(surveyRowId)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gps by viewModel.gpsState.collectAsStateWithLifecycle()
    val photoStore = appContainer().photoStore
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    // Save on every lifecycle pause: pressing Home, receiving a call, or the system
    // killing the app all leave the record on disk exactly as it was on screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    viewModel.saveNow()
                    viewModel.onStopLocation()
                }
                Lifecycle.Event.ON_START -> Unit
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.saveNow()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Survey Form", style = MaterialTheme.typography.titleLarge)
                        state.survey?.let {
                            Text(
                                text = it.surveyId,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.saveNow()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    AutoSaveBadge(state.autoSave, modifier = Modifier.padding(end = 12.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        val survey = state.survey

        if (state.loading || survey == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { SurveyorSection(state, viewModel) }
            item { SurveyIdSection(survey) }
            item { LocationSection(survey, state, viewModel) }
            item {
                PhotoSection(
                    survey = survey,
                    newPhotoTarget = viewModel::newPhotoTarget,
                    uriFor = photoStore::uriFor,
                    onCaptured = viewModel::onPhotoCaptured,
                    onPicked = viewModel::onPhotoPicked,
                    onDeleted = viewModel::onPhotoDeleted,
                    onDiscardTarget = viewModel::discardUnusedPhotoTarget
                )
            }
            item {
                GpsSection(
                    gps = gps,
                    onGetLocation = viewModel::onGetLocation,
                    onRefresh = viewModel::onRefreshLocation
                )
            }
            item { ShrubSection(survey, state, viewModel) }
            item { FruitSection(survey, state, viewModel) }
            item { HarvestSection(survey, state, viewModel) }
            item { ThankYouPanel() }
            item {
                Button(
                    onClick = { viewModel.finish { onFinished(surveyRowId) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 66.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Save & Finish Survey", style = MaterialTheme.typography.titleLarge)
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        viewModel.saveNow()
                        onBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text("Save Draft & Close")
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// --- Sections ---------------------------------------------------------------

@Composable
private fun SurveyorSection(state: SurveyFormUiState, viewModel: SurveyFormViewModel) {
    val survey = state.survey ?: return
    SectionCard(title = "Surveyor Information", subtitle = "Saved with every survey record") {
        SbtTextField(
            label = "Surveyor Name",
            value = survey.surveyorName,
            onValueChange = viewModel::onSurveyorNameChange,
            required = true,
            errorText = state.errors[Field.SURVEYOR_NAME]
        )
        SbtTextField(
            label = "Designation",
            value = survey.designation,
            onValueChange = viewModel::onDesignationChange,
            required = true,
            errorText = state.errors[Field.DESIGNATION]
        )

        Text(
            text = "Organization *",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OrganizationChoice(
                label = SurveyEntity.DEFAULT_ORGANIZATION,
                selected = !state.organizationIsOther,
                onClick = { viewModel.onOrganizationPresetSelected(false) },
                modifier = Modifier.weight(1f)
            )
            OrganizationChoice(
                label = "Other",
                selected = state.organizationIsOther,
                onClick = { viewModel.onOrganizationPresetSelected(true) },
                modifier = Modifier.widthIn(min = 110.dp)
            )
        }
        if (state.organizationIsOther) {
            SbtTextField(
                label = "Organization name",
                value = survey.organization,
                onValueChange = viewModel::onOrganizationChange,
                required = true,
                errorText = state.errors[Field.ORGANIZATION]
            )
        }
    }
}

@Composable
private fun OrganizationChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier.heightIn(min = 56.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 56.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SurveyIdSection(survey: SurveyEntity) {
    SectionCard(title = "Survey ID", number = 1, subtitle = "Generated automatically") {
        Text(
            text = survey.surveyId,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary
        )
        Row {
            Text(
                text = "Date",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(90.dp)
            )
            Text(Fmt.date(survey.date), style = MaterialTheme.typography.bodyLarge)
        }
        Row {
            Text(
                text = "Time",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(90.dp)
            )
            Text(survey.time.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun LocationSection(
    survey: SurveyEntity,
    state: SurveyFormUiState,
    viewModel: SurveyFormViewModel
) {
    SectionCard(title = "Location", number = 2) {
        SbtDropdownField(
            label = "District",
            value = survey.district,
            options = LadakhAdmin.districts,
            onValueChange = viewModel::onDistrictChange,
            required = true,
            errorText = state.errors[Field.DISTRICT]
        )
        SbtDropdownField(
            label = "Block",
            value = survey.block,
            options = LadakhAdmin.blocksFor(survey.district),
            onValueChange = viewModel::onBlockChange
        )
        SbtTextField(
            label = "Village",
            value = survey.village,
            onValueChange = viewModel::onVillageChange,
            required = true,
            errorText = state.errors[Field.VILLAGE]
        )
        SbtTextField(
            label = "Site",
            value = survey.site,
            onValueChange = viewModel::onSiteChange,
            supportingText = "Orchard, riverbank, roadside plantation, etc."
        )
    }
}

@Composable
private fun ShrubSection(
    survey: SurveyEntity,
    state: SurveyFormUiState,
    viewModel: SurveyFormViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionCard(title = "Shrub Type", number = 5) {
                OptionChips(
                    label = "Wood type",
                    options = ShrubType.entries,
                    selected = ShrubType.fromStorage(survey.shrubType),
                    onSelect = viewModel::onShrubTypeChange,
                    errorText = state.errors[Field.SHRUB_TYPE]
                )
            }

            SectionCard(title = "Plant Height", number = 6) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        SbtNumberField(
                            label = "Plant Height",
                            value = state.plantHeightText,
                            onValueChange = viewModel::onPlantHeightChange,
                            errorText = state.errors[Field.PLANT_HEIGHT]
                        )
                    }
                    UnitSelector(
                        options = HeightUnit.entries,
                        selected = HeightUnit.fromStorage(survey.plantHeightUnit),
                        onSelect = viewModel::onHeightUnitChange,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                val height = survey.plantHeight
                if (height != null && height > 0) {
                    val unit = HeightUnit.fromStorage(survey.plantHeightUnit)
                    Text(
                        text = String.format(
                            Locale.US,
                            "Plant Height: %.2f m  ·  %.2f ft",
                            height.toMetres(unit),
                            height.toFeet(unit)
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
    }
}

@Composable
private fun FruitSection(
    survey: SurveyEntity,
    state: SurveyFormUiState,
    viewModel: SurveyFormViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionCard(title = "Dominant Fruit Maturity Stage", number = 7) {
                OptionChips(
                    label = "Maturity stage",
                    options = MaturityStage.entries,
                    selected = MaturityStage.fromStorage(survey.maturityStage),
                    onSelect = viewModel::onMaturityStageChange,
                    errorText = state.errors[Field.MATURITY_STAGE]
                )
                SbtDateField(
                    label = "Harvest Date",
                    isoDate = survey.harvestDate,
                    onDateChange = viewModel::onHarvestDateChange,
                    supportingText = "Leave blank if harvesting has not occurred"
                )
            }

            SectionCard(title = "Berry Diameter", number = 8) {
                SbtNumberField(
                    label = "Berry Diameter",
                    value = state.berryDiameterText,
                    onValueChange = viewModel::onBerryDiameterChange,
                    suffix = "mm",
                    supportingText = "Example: 8.5 mm",
                    errorText = state.errors[Field.BERRY_DIAMETER]
                )
            }

            SectionCard(title = "TSS", number = 9) {
                SbtNumberField(
                    label = "TSS",
                    value = state.tssText,
                    onValueChange = viewModel::onTssChange,
                    suffix = "°Brix",
                    supportingText = "Example: 12.4 °Brix",
                    errorText = state.errors[Field.TSS]
                )
            }
    }
}

@Composable
private fun HarvestSection(
    survey: SurveyEntity,
    state: SurveyFormUiState,
    viewModel: SurveyFormViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionCard(title = "Ease of Harvest", number = 10) {
                OptionChips(
                    label = "How easily can the fruit be harvested?",
                    options = EaseOfHarvest.entries,
                    selected = EaseOfHarvest.fromStorage(survey.easeOfHarvest),
                    onSelect = viewModel::onEaseOfHarvestChange,
                    errorText = state.errors[Field.EASE_OF_HARVEST]
                )
            }

            SectionCard(title = "Fruit Shape Type", number = 11) {
                OptionChips(
                    label = "Shape category",
                    options = FruitShape.entries,
                    selected = FruitShape.fromStorage(survey.fruitShape),
                    onSelect = viewModel::onFruitShapeChange,
                    errorText = state.errors[Field.FRUIT_SHAPE]
                )
                if (survey.fruitShape == FruitShape.OTHER.storageValue) {
                    SbtTextField(
                        label = "Describe the shape",
                        value = survey.fruitShapeOther.orEmpty(),
                        onValueChange = viewModel::onFruitShapeOtherChange,
                        errorText = state.errors[Field.FRUIT_SHAPE_OTHER]
                    )
                }
            }
    }
}

@Composable
private fun ThankYouPanel() {
    SectionCard(title = "Thank You") {
        Text(
            text = "Thank you for contributing to the Seabuckthorn Field Survey.",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Krishi Vigyan Kendra – Leh",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
