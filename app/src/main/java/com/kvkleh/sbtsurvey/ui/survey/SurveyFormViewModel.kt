package com.kvkleh.sbtsurvey.ui.survey

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvkleh.sbtsurvey.data.local.SurveyEntity
import com.kvkleh.sbtsurvey.data.repo.SurveyRepository
import com.kvkleh.sbtsurvey.domain.EaseOfHarvest
import com.kvkleh.sbtsurvey.domain.FruitShape
import com.kvkleh.sbtsurvey.domain.HeightUnit
import com.kvkleh.sbtsurvey.domain.MaturityStage
import com.kvkleh.sbtsurvey.domain.ShrubType
import com.kvkleh.sbtsurvey.location.GpsState
import com.kvkleh.sbtsurvey.location.LocationController
import com.kvkleh.sbtsurvey.location.LocationFix
import com.kvkleh.sbtsurvey.photo.PhotoStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Drives the survey form, including the autosave machinery.
 *
 * Every edit updates the in-memory record immediately and schedules a debounced write.
 * Section changes, photo capture, GPS acquisition, backgrounding and Save & Finish all
 * force an immediate, non-debounced write. The record exists in the database from the
 * moment the form opens, so there is no window in which typed data lives only in memory.
 */
class SurveyFormViewModel(
    private val surveyRowId: Long,
    private val repository: SurveyRepository,
    private val photoStore: PhotoStore,
    context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SurveyFormUiState())
    val state: StateFlow<SurveyFormUiState> = _state.asStateFlow()

    val locationController = LocationController(context, viewModelScope)

    val gpsState: StateFlow<GpsState> get() = locationController.state

    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val survey = repository.getById(surveyRowId)
            if (survey == null) {
                _state.update {
                    it.copy(loading = false, message = "This survey could not be opened.")
                }
                return@launch
            }
            _state.update {
                it.copy(
                    loading = false,
                    survey = survey,
                    plantHeightText = survey.plantHeight?.let(::trimNumber).orEmpty(),
                    berryDiameterText = survey.berryDiameter?.let(::trimNumber).orEmpty(),
                    tssText = survey.tssBrix?.let(::trimNumber).orEmpty(),
                    organizationIsOther = survey.organization.isNotBlank() &&
                        survey.organization != SurveyEntity.DEFAULT_ORGANIZATION
                )
            }
            // Re-adopt a fix already stored on the record so re-opening a survey does not
            // discard the coordinates that were captured in the field.
            locationController.adoptSavedFix(survey.toFix())
        }

        // Mirror every accepted GPS fix onto the record.
        viewModelScope.launch {
            locationController.state.collect { gps ->
                val fix = gps.fix ?: return@collect
                val current = _state.value.survey ?: return@collect
                if (current.latitude == fix.latitude &&
                    current.longitude == fix.longitude &&
                    current.gpsAccuracy == fix.accuracy
                ) {
                    return@collect
                }
                edit {
                    it.copy(
                        latitude = fix.latitude,
                        longitude = fix.longitude,
                        altitude = fix.altitude,
                        gpsAccuracy = fix.accuracy,
                        gpsTimestamp = fix.timestamp
                    )
                }
                saveNow()
            }
        }
    }

    // --- Surveyor information -------------------------------------------------

    fun onSurveyorNameChange(value: String) = edit { it.copy(surveyorName = value) }

    fun onDesignationChange(value: String) = edit { it.copy(designation = value) }

    fun onOrganizationPresetSelected(isOther: Boolean) {
        _state.update { it.copy(organizationIsOther = isOther) }
        edit {
            it.copy(organization = if (isOther) "" else SurveyEntity.DEFAULT_ORGANIZATION)
        }
    }

    fun onOrganizationChange(value: String) = edit { it.copy(organization = value) }

    // --- Location -------------------------------------------------------------

    fun onDistrictChange(value: String) = edit { it.copy(district = value) }

    fun onBlockChange(value: String) = edit { it.copy(block = value) }

    fun onVillageChange(value: String) = edit { it.copy(village = value) }

    fun onSiteChange(value: String) = edit { it.copy(site = value) }

    // --- GPS ------------------------------------------------------------------

    fun onGetLocation() = locationController.start(force = false)

    fun onRefreshLocation() = locationController.refresh()

    fun onStopLocation() = locationController.stop()

    // --- Photo ----------------------------------------------------------------

    /** Destination handed to the camera before the capture intent is launched. */
    fun newPhotoTarget(): File? {
        val survey = _state.value.survey ?: return null
        return runCatching { photoStore.newPhotoFile(survey.surveyId) }.getOrNull()
    }

    fun onPhotoCaptured(file: File) {
        viewModelScope.launch {
            val survey = _state.value.survey ?: return@launch
            if (!file.exists() || file.length() == 0L) {
                file.delete()
                _state.update {
                    it.copy(message = "Camera is unavailable. Please check camera permission.")
                }
                return@launch
            }
            val updated = repository.setPhoto(survey, file, System.currentTimeMillis())
            _state.update { it.copy(survey = updated, autoSave = AutoSaveState.Saved(updated.updatedAt)) }
        }
    }

    fun onPhotoPicked(uri: Uri) {
        viewModelScope.launch {
            val survey = _state.value.survey ?: return@launch
            val copied = photoStore.importFrom(uri, survey.surveyId)
            if (copied == null) {
                _state.update { it.copy(message = "That image could not be copied into the survey.") }
                return@launch
            }
            val updated = repository.setPhoto(survey, copied, System.currentTimeMillis())
            _state.update { it.copy(survey = updated, autoSave = AutoSaveState.Saved(updated.updatedAt)) }
        }
    }

    fun onPhotoDeleted() {
        viewModelScope.launch {
            val survey = _state.value.survey ?: return@launch
            val updated = repository.setPhoto(survey, null, null)
            _state.update { it.copy(survey = updated, autoSave = AutoSaveState.Saved(updated.updatedAt)) }
        }
    }

    /** Discards a capture file the camera never actually wrote to. */
    fun discardUnusedPhotoTarget(file: File?) {
        if (file != null && file.exists() && file.length() == 0L) file.delete()
    }

    // --- Plant and fruit ------------------------------------------------------

    fun onShrubTypeChange(value: ShrubType) = edit { it.copy(shrubType = value.storageValue) }

    fun onPlantHeightChange(text: String) {
        _state.update { it.copy(plantHeightText = text) }
        edit { it.copy(plantHeight = text.toDoubleOrNull()) }
    }

    fun onHeightUnitChange(unit: HeightUnit) = edit { it.copy(plantHeightUnit = unit.storageValue) }

    fun onMaturityStageChange(value: MaturityStage) = edit { it.copy(maturityStage = value.storageValue) }

    fun onHarvestDateChange(isoDate: String?) = edit { it.copy(harvestDate = isoDate) }

    fun onBerryDiameterChange(text: String) {
        _state.update { it.copy(berryDiameterText = text) }
        edit { it.copy(berryDiameter = text.toDoubleOrNull()) }
    }

    fun onTssChange(text: String) {
        _state.update { it.copy(tssText = text) }
        edit { it.copy(tssBrix = text.toDoubleOrNull()) }
    }

    fun onEaseOfHarvestChange(value: EaseOfHarvest) = edit { it.copy(easeOfHarvest = value.storageValue) }

    fun onFruitShapeChange(value: FruitShape) = edit {
        it.copy(
            fruitShape = value.storageValue,
            fruitShapeOther = if (value == FruitShape.OTHER) it.fruitShapeOther else null
        )
    }

    fun onFruitShapeOtherChange(value: String) = edit { it.copy(fruitShapeOther = value) }

    // --- Saving ---------------------------------------------------------------

    /** Applies an edit in memory and schedules a debounced write. */
    private fun edit(transform: (SurveyEntity) -> SurveyEntity) {
        val current = _state.value.survey ?: return
        val updated = transform(current)
        if (updated == current) return
        _state.update { it.copy(survey = updated, autoSave = AutoSaveState.Saving) }
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MS)
            persist()
        }
    }

    /**
     * Writes immediately, bypassing the debounce. Called when the surveyor moves between
     * sections, when the app is backgrounded, and before finishing.
     */
    fun saveNow() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch { persist() }
    }

    private suspend fun persist() {
        val survey = _state.value.survey ?: return
        try {
            repository.save(survey)
            _state.update { it.copy(autoSave = AutoSaveState.Saved(System.currentTimeMillis())) }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    autoSave = AutoSaveState.Failed("Not saved"),
                    message = "Survey could not be saved. Please try again."
                )
            }
        }
    }

    /**
     * Validates and completes the survey.
     *
     * @return true when the record was marked complete; false leaves validation messages
     *         attached to the offending fields.
     */
    fun finish(onCompleted: () -> Unit) {
        val survey = _state.value.survey ?: return
        val errors = validate(survey, _state.value)
        if (errors.isNotEmpty()) {
            _state.update {
                it.copy(
                    errors = errors,
                    message = "Please correct the highlighted fields before finishing."
                )
            }
            return
        }
        viewModelScope.launch {
            saveJob?.cancel()
            try {
                repository.complete(survey)
                repository.backupDatabase()
                _state.update {
                    it.copy(
                        errors = emptyMap(),
                        autoSave = AutoSaveState.Saved(System.currentTimeMillis()),
                        finished = true
                    )
                }
                onCompleted()
            } catch (e: Exception) {
                _state.update { it.copy(message = "Survey could not be saved. Please try again.") }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        locationController.stop()
        super.onCleared()
    }

    companion object {
        private const val AUTOSAVE_DEBOUNCE_MS = 400L

        /** Field-level validation shared by the form and the finish action. */
        fun validate(survey: SurveyEntity, state: SurveyFormUiState): Map<String, String> {
            val errors = mutableMapOf<String, String>()

            if (survey.surveyorName.isBlank()) errors[Field.SURVEYOR_NAME] = "Surveyor name is required"
            if (survey.designation.isBlank()) errors[Field.DESIGNATION] = "Designation is required"
            if (survey.organization.isBlank()) errors[Field.ORGANIZATION] = "Organization is required"
            if (survey.district.isBlank()) errors[Field.DISTRICT] = "District is required"
            if (survey.village.isBlank()) errors[Field.VILLAGE] = "Village is required"

            numericError(state.plantHeightText, min = 0.0, max = 20.0, unit = "m/ft")
                ?.let { errors[Field.PLANT_HEIGHT] = it }
            numericError(state.berryDiameterText, min = 0.0, max = 100.0, unit = "mm")
                ?.let { errors[Field.BERRY_DIAMETER] = it }
            numericError(state.tssText, min = 0.0, max = 100.0, unit = "°Brix")
                ?.let { errors[Field.TSS] = it }

            if (survey.fruitShape == FruitShape.OTHER.storageValue &&
                survey.fruitShapeOther.isNullOrBlank()
            ) {
                errors[Field.FRUIT_SHAPE_OTHER] = "Describe the fruit shape"
            }
            return errors
        }

        /** Optional numeric input: blank is fine, nonsense is not. */
        private fun numericError(text: String, min: Double, max: Double, unit: String): String? {
            if (text.isBlank()) return null
            val value = text.toDoubleOrNull() ?: return "Enter a number"
            if (value <= min) return "Must be greater than $min"
            if (value > max) return "Must not exceed $max $unit"
            return null
        }

        fun trimNumber(value: Double): String =
            if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    }
}

private fun SurveyEntity.toFix(): LocationFix? {
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    return LocationFix(
        latitude = lat,
        longitude = lon,
        altitude = altitude,
        accuracy = gpsAccuracy,
        timestamp = gpsTimestamp ?: updatedAt
    )
}
