package com.kvk.leh.seabuckthorn.ui.survey.wizard

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kvk.leh.seabuckthorn.data.local.entity.PhotoEntity
import com.kvk.leh.seabuckthorn.ui.components.ConfirmDialog
import com.kvk.leh.seabuckthorn.ui.components.WizardScaffold
import com.kvk.leh.seabuckthorn.ui.navigation.Destinations
import com.kvk.leh.seabuckthorn.ui.photo.PhotoCaptureOverlay
import java.time.Instant

/**
 * Hosts the full new-survey / edit-survey wizard: 8 steps, a full-screen camera overlay for the
 * Photos step, and the review/save flow. All child-step composables are stateless — this screen
 * owns navigation between steps and wires callbacks to [SurveyWizardViewModel].
 */
@Composable
fun SurveyWizardScreen(
    viewModel: SurveyWizardViewModel,
    surveyId: String,
    onSaved: () -> Unit,
    onClose: () -> Unit
) {
    val state by viewModel.formState.collectAsState()
    val currentStep by viewModel.currentStep.collectAsState()
    val isCapturingGps by viewModel.isCapturingGps.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()

    var showCamera by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(surveyId) {
        if (surveyId == Destinations.NEW_SURVEY_ID) viewModel.initializeNew() else viewModel.loadExisting(surveyId)
    }

    if (showCamera) {
        PhotoCaptureOverlay(
            surveyCode = state.surveyCode,
            latitude = state.latitude,
            longitude = state.longitude,
            altitude = state.altitude,
            nextPhotoNumber = state.photos.size + 1,
            onCaptured = { filePath, category, watermarked ->
                viewModel.onPhotoAdded(
                    PhotoEntity(
                        surveyId = state.surveyId,
                        filePath = filePath,
                        category = category.name,
                        photoNumber = state.photos.size + 1,
                        capturedAt = Instant.now().toEpochMilli(),
                        latitude = state.latitude,
                        longitude = state.longitude,
                        altitude = state.altitude,
                        isWatermarked = watermarked
                    )
                )
                showCamera = false
            },
            onClose = { showCamera = false }
        )
        return
    }

    val steps = WizardStep.entries
    val stepIndex = steps.indexOf(currentStep)

    WizardScaffold(
        title = currentStep.title,
        stepIndex = stepIndex,
        stepCount = steps.size,
        onBack = viewModel::goBack,
        onNext = {
            if (currentStep == WizardStep.REVIEW) viewModel.saveSurvey() else viewModel.goNext()
        },
        nextLabel = if (currentStep == WizardStep.REVIEW) "Save Survey" else "Next",
        onClose = { showExitConfirm = true }
    ) { modifier ->
        when (currentStep) {
            WizardStep.SURVEY_INFO -> Step1SurveyInfoScreen(state, viewModel::update, modifier)
            WizardStep.LOCATION -> Step2LocationScreen(
                state, isCapturingGps,
                onStartCapture = viewModel::startGpsCapture,
                onStopCapture = viewModel::stopGpsCapture,
                onMarkUnavailable = viewModel::markGpsUnavailable,
                modifier = modifier
            )
            WizardStep.SHRUB -> Step3ShrubScreen(state, viewModel::update, modifier)
            WizardStep.PHENOLOGY -> Step4PhenologyScreen(state, viewModel::update, modifier)
            WizardStep.FRUIT -> Step5FruitScreen(
                state, viewModel::update,
                onAddBerry = viewModel::addBerryRow,
                onRemoveBerry = viewModel::removeBerryRow,
                onUpdateBerry = viewModel::addOrUpdateBerry,
                modifier = modifier
            )
            WizardStep.ENVIRONMENT -> Step6EnvironmentScreen(state, viewModel::update, modifier)
            WizardStep.PHOTOS -> Step7PhotosScreen(
                photos = state.photos,
                onAddPhoto = { showCamera = true },
                onRemovePhoto = viewModel::onPhotoRemoved,
                modifier = modifier
            )
            WizardStep.REVIEW -> Step8ReviewScreen(state, modifier)
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Save draft and exit?") },
            text = { Text("Your progress will be saved as a draft. You can continue this survey later from the survey list.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.persistDraft()
                    showExitConfirm = false
                    onClose()
                }) { Text("Save Draft & Exit") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text("Cancel") }
            }
        )
    }

    saveResult?.let { result ->
        when (result) {
            is SaveResult.Success -> {
                ConfirmDialog(
                    title = "Survey saved successfully",
                    message = "${result.surveyCode} — Data stored offline.",
                    confirmLabel = "OK",
                    onConfirm = { viewModel.clearSaveResult(); onSaved() },
                    onDismiss = { viewModel.clearSaveResult(); onSaved() }
                )
            }
            is SaveResult.Error -> {
                ConfirmDialog(
                    title = "Could not save survey",
                    message = result.message,
                    confirmLabel = "OK",
                    onConfirm = { viewModel.clearSaveResult() },
                    onDismiss = { viewModel.clearSaveResult() }
                )
            }
        }
    }
}
