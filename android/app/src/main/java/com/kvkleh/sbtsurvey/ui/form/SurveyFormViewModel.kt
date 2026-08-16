package com.kvkleh.sbtsurvey.ui.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvkleh.sbtsurvey.Graph
import com.kvkleh.sbtsurvey.data.Formats
import com.kvkleh.sbtsurvey.data.SurveyField
import com.kvkleh.sbtsurvey.data.SurveyOptions
import com.kvkleh.sbtsurvey.data.SurveyValidator
import com.kvkleh.sbtsurvey.data.db.SurveyEntity
import com.kvkleh.sbtsurvey.data.location.GpsStatus
import com.kvkleh.sbtsurvey.ui.components.AutoSaveState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SurveyFormUiState(
    val loading: Boolean = true,
    val survey: SurveyEntity? = null,
    val heightText: String = "",
    val berryDiameterText: String = "",
    val tssText: String = "",
    val autoSave: AutoSaveState = AutoSaveState.Idle,
    val gpsStatus: GpsStatus = GpsStatus.Acquiring,
    val gpsLocked: Boolean = false,
    val knownVillages: List<String> = emptyList(),
    val errors: Map<String, String> = emptyMap(),
    val showErrors: Boolean = false,
    val message: String? = null
) {
    fun errorFor(field: String): String? = if (showErrors) errors[field] else null
}

/**
 * Drives the survey form.
 *
 * Every edit lands in the database within [AUTO_SAVE_DELAY_MS], and the form is
 * flushed immediately when the screen is left, so closing the app, a battery pull
 * or a crash costs at most the keystroke in progress.
 */
class SurveyFormViewModel : ViewModel() {

    private val repository = Graph.repository
    private val locationService = Graph.locationService

    private val _uiState = MutableStateFlow(SurveyFormUiState())
    val uiState: StateFlow<SurveyFormUiState> = _uiState.asStateFlow()

    private var loadedId: Long = 0L
    private var autoSaveJob: Job? = null
    private var gpsJob: Job? = null

    fun load(id: Long) {
        if (loadedId == id && _uiState.value.survey != null) {
            // Returning from the camera: the photo was linked to the record while
            // this form was off screen, so pick that up without touching edits.
            refreshPhotoFromDatabase()
            return
        }
        loadedId = id
        viewModelScope.launch {
            val survey = repository.getById(id)
            _uiState.value = _uiState.value.copy(
                loading = false,
                survey = survey,
                heightText = Formats.number(survey?.plantHeight),
                berryDiameterText = Formats.number(survey?.berryDiameterMm),
                tssText = Formats.number(survey?.tssBrix),
                // A record that already carries coordinates keeps them; a fresh one
                // takes the first good fix automatically.
                gpsLocked = survey?.hasLocation == true
            )
            startLocationUpdates()
        }
        viewModelScope.launch {
            repository.observeKnownVillages().collect { villages ->
                _uiState.value = _uiState.value.copy(knownVillages = villages)
            }
        }
    }

    // --- editing ---------------------------------------------------------

    private fun edit(transform: (SurveyEntity) -> SurveyEntity) {
        val current = _uiState.value.survey ?: return
        val updated = transform(current)
        _uiState.value = _uiState.value.copy(survey = updated)
        revalidate()
        scheduleAutoSave()
    }

    fun setSurveyorName(value: String) = edit { it.copy(surveyorName = value) }
    fun setDesignation(value: String) = edit { it.copy(designation = value) }
    fun setOrganization(value: String) = edit { it.copy(organization = value) }

    fun setDistrict(value: String) = edit {
        // Changing district invalidates a block that belongs to the other district.
        val keepBlock = SurveyOptions.blocksFor(value).contains(it.block)
        it.copy(district = value, block = if (keepBlock) it.block else "")
    }

    fun setBlock(value: String) = edit { it.copy(block = value) }
    fun setVillage(value: String) = edit { it.copy(village = value) }
    fun setSite(value: String) = edit { it.copy(site = value) }
    fun setShrubType(value: String) = edit { it.copy(shrubType = value) }
    fun setMaturityStage(value: String) = edit { it.copy(maturityStage = value) }
    fun setEaseOfHarvest(value: String) = edit { it.copy(easeOfHarvest = value) }
    fun setHarvestDate(millis: Long?) = edit {
        it.copy(harvestDate = millis?.let(Formats::startOfDay))
    }

    fun setHeightUnit(unit: String) = edit { it.copy(plantHeightUnit = unit) }

    fun setHeightText(text: String) {
        _uiState.value = _uiState.value.copy(heightText = text)
        edit { it.copy(plantHeight = Formats.parseNumber(text)) }
    }

    fun setBerryDiameterText(text: String) {
        _uiState.value = _uiState.value.copy(berryDiameterText = text)
        edit { it.copy(berryDiameterMm = Formats.parseNumber(text)) }
    }

    fun setTssText(text: String) {
        _uiState.value = _uiState.value.copy(tssText = text)
        edit { it.copy(tssBrix = Formats.parseNumber(text)) }
    }

    fun onPhotoCaptured(absolutePath: String, fileName: String) {
        edit { it.copy(photoPath = absolutePath, photoFileName = fileName) }
        flushNow()
    }

    private fun refreshPhotoFromDatabase() {
        viewModelScope.launch {
            val stored = repository.getById(loadedId) ?: return@launch
            val current = _uiState.value.survey ?: return@launch
            if (stored.photoPath != current.photoPath ||
                stored.photoFileName != current.photoFileName
            ) {
                _uiState.value = _uiState.value.copy(
                    survey = current.copy(
                        photoPath = stored.photoPath,
                        photoFileName = stored.photoFileName
                    )
                )
            }
        }
    }

    fun clearPhoto() {
        val survey = _uiState.value.survey ?: return
        viewModelScope.launch {
            repository.clearPhoto(survey.id)
            _uiState.value = _uiState.value.copy(
                survey = _uiState.value.survey?.copy(photoPath = null, photoFileName = null)
            )
        }
    }

    // --- auto-save -------------------------------------------------------

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        _uiState.value = _uiState.value.copy(autoSave = AutoSaveState.Saving)
        autoSaveJob = viewModelScope.launch {
            delay(AUTO_SAVE_DELAY_MS)
            persist()
        }
    }

    /**
     * Writes the current form immediately. Runs on the application scope rather
     * than the view-model scope so that the last write still completes when the
     * screen — and with it this view model — is being destroyed.
     */
    fun flushNow() {
        autoSaveJob?.cancel()
        autoSaveJob = Graph.appScope.launch { persist() }
    }

    private suspend fun persist() {
        val survey = _uiState.value.survey ?: return
        val result = runCatching { repository.saveDraft(survey) }
        _uiState.value = _uiState.value.copy(
            autoSave = if (result.isSuccess) AutoSaveState.Saved else AutoSaveState.Failed,
            message = if (result.isSuccess) null else "Could not save to the device database"
        )
    }

    // --- GPS -------------------------------------------------------------

    fun startLocationUpdates() {
        gpsJob?.cancel()
        gpsJob = viewModelScope.launch {
            locationService.statusUpdates().collect { status ->
                _uiState.value = _uiState.value.copy(gpsStatus = status)
                if (status is GpsStatus.Ready && !_uiState.value.gpsLocked) {
                    applyFix(status)
                }
            }
        }
    }

    fun stopLocationUpdates() {
        gpsJob?.cancel()
        gpsJob = null
    }

    /** Replaces the stored position with the current best fix. */
    fun captureLocationNow() {
        val status = _uiState.value.gpsStatus
        if (status is GpsStatus.Ready) {
            applyFix(status)
        } else {
            _uiState.value = _uiState.value.copy(gpsLocked = false)
            startLocationUpdates()
        }
    }

    private fun applyFix(status: GpsStatus.Ready) {
        val fix = status.fix
        _uiState.value = _uiState.value.copy(gpsLocked = true)
        edit {
            it.copy(
                latitude = fix.latitude,
                longitude = fix.longitude,
                altitude = fix.altitude,
                accuracyM = fix.accuracyM,
                locationCapturedAt = fix.capturedAt
            )
        }
    }

    /** Frees the stored fix so a better reading can be taken at the plant. */
    fun unlockLocation() {
        _uiState.value = _uiState.value.copy(gpsLocked = false)
        startLocationUpdates()
    }

    // --- validation ------------------------------------------------------

    private fun revalidate() {
        val state = _uiState.value
        val survey = state.survey ?: return
        val result = SurveyValidator.validate(
            survey = survey,
            heightText = state.heightText,
            berryText = state.berryDiameterText,
            tssText = state.tssText
        )
        _uiState.value = _uiState.value.copy(errors = result.errors)
    }

    /**
     * Checks only the surveyor block, so the first step can be left as soon as
     * those three fields are filled in.
     */
    fun validateSurveyor(): Boolean {
        val state = _uiState.value
        val survey = state.survey ?: return false
        val result = SurveyValidator.validate(survey, state.heightText, state.berryDiameterText, state.tssText)
        val surveyorErrors = result.errors.filterKeys { it in SURVEYOR_FIELDS }
        _uiState.value = _uiState.value.copy(errors = result.errors, showErrors = true)
        if (surveyorErrors.isEmpty()) flushNow()
        return surveyorErrors.isEmpty()
    }

    /** Returns true when the form may move on to the review screen. */
    fun validateForReview(): Boolean {
        val state = _uiState.value
        val survey = state.survey ?: return false
        val result = SurveyValidator.validate(
            survey = survey,
            heightText = state.heightText,
            berryText = state.berryDiameterText,
            tssText = state.tssText
        )
        _uiState.value = _uiState.value.copy(errors = result.errors, showErrors = true)
        if (result.isValid) flushNow()
        return result.isValid
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    override fun onCleared() {
        super.onCleared()
        gpsJob?.cancel()
    }

    private companion object {
        const val AUTO_SAVE_DELAY_MS = 400L

        val SURVEYOR_FIELDS = setOf(
            SurveyField.SURVEYOR_NAME,
            SurveyField.DESIGNATION,
            SurveyField.ORGANIZATION
        )
    }
}
