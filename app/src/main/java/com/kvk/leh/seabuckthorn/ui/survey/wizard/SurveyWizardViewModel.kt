package com.kvk.leh.seabuckthorn.ui.survey.wizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvk.leh.seabuckthorn.data.local.entity.*
import com.kvk.leh.seabuckthorn.data.preferences.AppPreferences
import com.kvk.leh.seabuckthorn.data.repository.SurveyRepository
import com.kvk.leh.seabuckthorn.domain.SurveyCodeGenerator
import com.kvk.leh.seabuckthorn.domain.calculations.CanopyCalculations
import com.kvk.leh.seabuckthorn.domain.model.GpsStatus
import com.kvk.leh.seabuckthorn.domain.model.SurveyRecord
import com.kvk.leh.seabuckthorn.location.GpsFix
import com.kvk.leh.seabuckthorn.location.LocationTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

enum class WizardStep(val title: String) {
    SURVEY_INFO("Survey Information"),
    LOCATION("GPS Location"),
    SHRUB("Shrub Characteristics"),
    PHENOLOGY("Fruit Phenology & Maturity"),
    FRUIT("Fruit Measurements & Quality"),
    ENVIRONMENT("Site & Environment"),
    PHOTOS("Photographs"),
    REVIEW("Review & Save")
}

sealed class SaveResult {
    data class Success(val surveyCode: String) : SaveResult()
    data class Error(val message: String) : SaveResult()
}

class SurveyWizardViewModel(
    private val repository: SurveyRepository,
    private val codeGenerator: SurveyCodeGenerator,
    private val appPreferences: AppPreferences,
    private val locationTracker: LocationTracker
) : ViewModel() {

    private val _formState = MutableStateFlow(SurveyFormState())
    val formState: StateFlow<SurveyFormState> = _formState.asStateFlow()

    private val _currentStep = MutableStateFlow(WizardStep.SURVEY_INFO)
    val currentStep: StateFlow<WizardStep> = _currentStep.asStateFlow()

    private val _isCapturingGps = MutableStateFlow(false)
    val isCapturingGps: StateFlow<Boolean> = _isCapturingGps.asStateFlow()

    private val _saveResult = MutableStateFlow<SaveResult?>(null)
    val saveResult: StateFlow<SaveResult?> = _saveResult.asStateFlow()

    private var gpsJob: Job? = null

    fun initializeNew() {
        viewModelScope.launch {
            val profile = appPreferences.currentProfile()
            val code = codeGenerator.nextCode()
            _formState.value = SurveyFormState(
                surveyId = UUID.randomUUID().toString(),
                surveyCode = code,
                surveyorName = profile.name,
                isEditingExisting = false
            )
            _currentStep.value = WizardStep.SURVEY_INFO
            // Persist a draft row immediately so photos captured mid-wizard have a stable
            // survey to attach to, and so the in-progress survey survives an app kill.
            persistDraft()
        }
    }

    /** Saves current progress as a draft (isDraft = true). Safe to call repeatedly. */
    fun persistDraft() {
        viewModelScope.launch {
            runCatching { repository.saveSurveyRecord(_formState.value.toSurveyRecord(isDraft = true)) }
        }
    }

    fun loadExisting(surveyId: String) {
        viewModelScope.launch {
            val record = repository.getSurveyRecord(surveyId) ?: return@launch
            _formState.value = record.toFormState()
            _currentStep.value = WizardStep.SURVEY_INFO
        }
    }

    fun update(transform: (SurveyFormState) -> SurveyFormState) {
        _formState.value = transform(_formState.value)
    }

    fun goToStep(step: WizardStep) { _currentStep.value = step }

    fun goNext() {
        val steps = WizardStep.entries
        val index = steps.indexOf(_currentStep.value)
        if (index < steps.lastIndex) _currentStep.value = steps[index + 1]
        persistDraft()
    }

    fun goBack() {
        val steps = WizardStep.entries
        val index = steps.indexOf(_currentStep.value)
        if (index > 0) _currentStep.value = steps[index - 1]
    }

    /** Starts a live GPS capture session; UI observes [formState] for continuously-improving accuracy. */
    fun startGpsCapture() {
        if (!locationTracker.hasLocationPermission()) {
            update { it.copy(gpsStatus = GpsStatus.UNAVAILABLE) }
            return
        }
        gpsJob?.cancel()
        _isCapturingGps.value = true
        gpsJob = viewModelScope.launch {
            locationTracker.observeFixes().collect { fix: GpsFix ->
                update {
                    it.copy(
                        latitude = fix.latitude,
                        longitude = fix.longitude,
                        altitude = fix.altitudeMeters,
                        gpsAccuracyM = fix.accuracyMeters,
                        gpsAcquiredAt = fix.acquiredAtMillis,
                        gpsStatus = GpsStatus.CAPTURED
                    )
                }
            }
        }
    }

    fun stopGpsCapture() {
        gpsJob?.cancel()
        gpsJob = null
        _isCapturingGps.value = false
    }

    fun markGpsUnavailable() {
        stopGpsCapture()
        update {
            it.copy(
                latitude = null, longitude = null, altitude = null,
                gpsAccuracyM = null, gpsAcquiredAt = null, gpsStatus = GpsStatus.UNAVAILABLE
            )
        }
    }

    fun addOrUpdateBerry(row: BerryRow) {
        update { state ->
            val existing = state.berries.toMutableList()
            val idx = existing.indexOfFirst { it.index == row.index }
            if (idx >= 0) existing[idx] = row else existing.add(row)
            state.copy(berries = existing.sortedBy { it.index })
        }
    }

    fun addBerryRow() {
        update { state ->
            val nextIndex = (state.berries.maxOfOrNull { it.index } ?: 0) + 1
            state.copy(berries = state.berries + BerryRow(nextIndex))
        }
    }

    fun removeBerryRow(index: Int) {
        update { state -> state.copy(berries = state.berries.filterNot { it.index == index }) }
    }

    fun onPhotoAdded(photo: PhotoEntity) {
        update { it.copy(photos = it.photos + photo) }
    }

    fun onPhotoRemoved(photo: PhotoEntity) {
        viewModelScope.launch {
            repository.deletePhoto(photo)
            update { it.copy(photos = it.photos.filterNot { p -> p.id == photo.id }) }
        }
    }

    fun saveSurvey() {
        viewModelScope.launch {
            val state = _formState.value
            runCatching {
                val record = state.toSurveyRecord(isDraft = false)
                repository.saveSurveyRecord(record)
            }.onSuccess {
                _saveResult.value = SaveResult.Success(state.surveyCode)
            }.onFailure { e ->
                _saveResult.value = SaveResult.Error(e.message ?: "Could not save survey")
            }
        }
    }

    fun clearSaveResult() { _saveResult.value = null }

    override fun onCleared() {
        super.onCleared()
        gpsJob?.cancel()
    }
}

private fun SurveyRecord.toFormState(): SurveyFormState {
    val s = survey
    val sh = shrub
    val ph = phenology
    val fq = fruitQuality
    val env = environment
    return SurveyFormState(
        surveyId = s.id,
        surveyCode = s.surveyCode,
        isDraft = s.isDraft,
        isEditingExisting = true,
        surveyDateEpochDay = s.surveyDateEpochDay,
        surveyTime = s.surveyTime,
        surveyorName = s.surveyorName,
        district = s.district,
        block = s.block,
        village = s.village,
        siteName = s.siteName,
        landUseType = runCatching { com.kvk.leh.seabuckthorn.domain.model.LandUseType.valueOf(s.landUseType) }.getOrDefault(com.kvk.leh.seabuckthorn.domain.model.LandUseType.NATURAL_STAND),
        landUseOther = s.landUseOther ?: "",
        ownershipStatus = runCatching { com.kvk.leh.seabuckthorn.domain.model.OwnershipStatus.valueOf(s.ownershipStatus) }.getOrDefault(com.kvk.leh.seabuckthorn.domain.model.OwnershipStatus.UNKNOWN),
        remarks = s.remarks ?: "",
        latitude = s.latitude,
        longitude = s.longitude,
        altitude = s.altitude,
        gpsAccuracyM = s.gpsAccuracyM,
        gpsAcquiredAt = s.gpsAcquiredAt,
        gpsStatus = runCatching { GpsStatus.valueOf(s.gpsStatus) }.getOrDefault(GpsStatus.UNAVAILABLE),
        shrubType = sh?.shrubType?.let { runCatching { com.kvk.leh.seabuckthorn.domain.model.ShrubType.valueOf(it) }.getOrNull() } ?: com.kvk.leh.seabuckthorn.domain.model.ShrubType.UNKNOWN,
        growthForm = sh?.growthForm?.let { runCatching { com.kvk.leh.seabuckthorn.domain.model.GrowthForm.valueOf(it) }.getOrNull() } ?: com.kvk.leh.seabuckthorn.domain.model.GrowthForm.ERECT,
        plantHeightM = sh?.plantHeightM,
        canopyNsM = sh?.canopyNsM,
        canopyEwM = sh?.canopyEwM,
        stemCircumferenceCm = sh?.stemCircumferenceCm,
        numMajorStems = sh?.numMajorStems?.toDouble(),
        estimatedAgeYears = sh?.estimatedAgeYears,
        ageEstimationMethod = sh?.ageEstimationMethod ?: "",
        plantDensityPerHa = sh?.plantDensityPerHa,
        approxSpacingM = sh?.approxSpacingM,
        regenerationStatus = sh?.regenerationStatus?.let { runCatching { com.kvk.leh.seabuckthorn.domain.model.RegenerationStatus.valueOf(it) }.getOrNull() } ?: com.kvk.leh.seabuckthorn.domain.model.RegenerationStatus.MODERATE,
        presenceOfSuckers = sh?.presenceOfSuckers ?: false,
        floweringStatus = sh?.floweringStatus ?: false,
        fruitingStatus = sh?.fruitingStatus ?: false,
        numBranches = sh?.numBranches?.toDouble(),
        mainStemDiameterCm = sh?.mainStemDiameterCm,
        branchDiameterCm = sh?.branchDiameterCm,
        leafColor = sh?.leafColor ?: "",
        leafDensity = sh?.leafDensity?.let { runCatching { com.kvk.leh.seabuckthorn.domain.model.Level4.valueOf(it) }.getOrNull() } ?: com.kvk.leh.seabuckthorn.domain.model.Level4.MODERATE,
        canopyCoverPercent = sh?.canopyCoverPercent,
        thornDensity = sh?.thornDensity?.let { runCatching { com.kvk.leh.seabuckthorn.domain.model.Level4.valueOf(it) }.getOrNull() } ?: com.kvk.leh.seabuckthorn.domain.model.Level4.MODERATE,
        suckerAbundance = sh?.suckerAbundance?.let { runCatching { com.kvk.leh.seabuckthorn.domain.model.Level4.valueOf(it) }.getOrNull() } ?: com.kvk.leh.seabuckthorn.domain.model.Level4.NONE,
        overallHealth = sh?.overallHealth?.let { runCatching { com.kvk.leh.seabuckthorn.domain.model.HealthStatus.valueOf(it) }.getOrNull() } ?: com.kvk.leh.seabuckthorn.domain.model.HealthStatus.GOOD,
        pestIncidence = sh?.pestIncidence?.let { runCatching { com.kvk.leh.seabuckthorn.domain.model.Level4.valueOf(it) }.getOrNull() } ?: com.kvk.leh.seabuckthorn.domain.model.Level4.NONE,
        diseaseIncidence = sh?.diseaseIncidence?.let { runCatching { com.kvk.leh.seabuckthorn.domain.model.Level4.valueOf(it) }.getOrNull() } ?: com.kvk.leh.seabuckthorn.domain.model.Level4.NONE,
        browsingDamage = sh?.browsingDamage?.let { runCatching { com.kvk.leh.seabuckthorn.domain.model.Level4.valueOf(it) }.getOrNull() } ?: com.kvk.leh.seabuckthorn.domain.model.Level4.NONE,
        mechanicalDamage = sh?.mechanicalDamage?.let { runCatching { com.kvk.leh.seabuckthorn.domain.model.Level4.valueOf(it) }.getOrNull() } ?: com.kvk.leh.seabuckthorn.domain.model.Level4.NONE,
        droughtStress = sh?.droughtStress?.let { runCatching { com.kvk.leh.seabuckthorn.domain.model.Level4.valueOf(it) }.getOrNull() } ?: com.kvk.leh.seabuckthorn.domain.model.Level4.NONE,
        otherStressSymptoms = sh?.otherStressSymptoms ?: "",
        dominantMaturityStage = ph?.dominantMaturityStage?.let { runCatching { com.kvk.leh.seabuckthorn.domain.model.MaturityStage.valueOf(it) }.getOrNull() },
        pctUnripe = ph?.pctUnripe,
        pctIntermediate = ph?.pctIntermediate,
        pctRipe = ph?.pctRipe,
        pctOverripe = ph?.pctOverripe,
        floweringInitiationDate = PhenoDate(ph?.floweringInitiationDate, ph?.floweringInitiationEstimated ?: true),
        floweringPeakDate = PhenoDate(ph?.floweringPeakDate, ph?.floweringPeakEstimated ?: true),
        fruitSetDate = PhenoDate(ph?.fruitSetDate, ph?.fruitSetEstimated ?: true),
        fruitDevelopmentInitiationDate = PhenoDate(ph?.fruitDevelopmentInitiationDate, ph?.fruitDevelopmentInitiationEstimated ?: true),
        firstColorChangeDate = PhenoDate(ph?.firstColorChangeDate, ph?.firstColorChangeEstimated ?: true),
        firstMaturityDate = PhenoDate(ph?.firstMaturityDate, ph?.firstMaturityEstimated ?: true),
        fiftyPercentMaturityDate = PhenoDate(ph?.fiftyPercentMaturityDate, ph?.fiftyPercentMaturityEstimated ?: true),
        peakMaturityDate = PhenoDate(ph?.peakMaturityDate, ph?.peakMaturityEstimated ?: true),
        harvestInitiationDate = PhenoDate(ph?.harvestInitiationDate, ph?.harvestInitiationEstimated ?: true),
        estimatedFullMaturityDate = PhenoDate(ph?.estimatedFullMaturityDate, ph?.estimatedFullMaturityEstimated ?: true),
        berries = berries.map { BerryRow(it.berryIndex, it.lengthMm, it.widthMm, it.weightG) }.ifEmpty { listOf(BerryRow(1)) },
        fruitColor = fq?.fruitColor?.let { runCatching { com.kvk.leh.seabuckthorn.domain.model.FruitColor.valueOf(it) }.getOrNull() } ?: com.kvk.leh.seabuckthorn.domain.model.FruitColor.ORANGE,
        fruitColorOther = fq?.fruitColorOther ?: "",
        fruitFirmness = fq?.fruitFirmness?.let { runCatching { com.kvk.leh.seabuckthorn.domain.model.FruitFirmness.valueOf(it) }.getOrNull() } ?: com.kvk.leh.seabuckthorn.domain.model.FruitFirmness.MODERATE,
        fruitShape = fq?.fruitShape?.let { runCatching { com.kvk.leh.seabuckthorn.domain.model.FruitShape.valueOf(it) }.getOrNull() } ?: com.kvk.leh.seabuckthorn.domain.model.FruitShape.ROUND,
        fruitShapeOther = fq?.fruitShapeOther ?: "",
        berriesPerCluster = fq?.berriesPerCluster,
        tssBrix = fq?.tssBrix,
        ph = fq?.ph,
        juiceYieldPercent = fq?.juiceYieldPercent,
        titratableAcidityPercent = fq?.titratableAcidityPercent,
        vitaminCMgPer100g = fq?.vitaminCMgPer100g,
        totalCarotenoidsMgPer100g = fq?.totalCarotenoidsMgPer100g,
        otherLabParams = fq?.otherLabParams ?: "",
        fruitsPerBranch = fq?.fruitsPerBranch,
        fruitsPerCluster = fq?.fruitsPerCluster,
        estimatedYieldKgPerShrub = fq?.estimatedYieldKgPerShrub,
        fruitBearingBranchPercent = fq?.fruitBearingBranchPercent,
        berryColorIntensity = fq?.berryColorIntensity?.let { runCatching { com.kvk.leh.seabuckthorn.domain.model.ColorIntensity.valueOf(it) }.getOrNull() } ?: com.kvk.leh.seabuckthorn.domain.model.ColorIntensity.MEDIUM,
        berryDetachmentEase = fq?.berryDetachmentEase?.let { runCatching { com.kvk.leh.seabuckthorn.domain.model.EaseLevel.valueOf(it) }.getOrNull() } ?: com.kvk.leh.seabuckthorn.domain.model.EaseLevel.MODERATE,
        fruitDamagePercent = fq?.fruitDamagePercent,
        slopePercent = env?.slopePercent,
        aspect = env?.aspect?.let { runCatching { com.kvk.leh.seabuckthorn.domain.model.Aspect.valueOf(it) }.getOrNull() } ?: com.kvk.leh.seabuckthorn.domain.model.Aspect.FLAT,
        soilSurfaceCondition = env?.soilSurfaceCondition ?: "",
        soilMoistureClass = env?.soilMoistureClass?.let { runCatching { com.kvk.leh.seabuckthorn.domain.model.SoilMoistureClass.valueOf(it) }.getOrNull() } ?: com.kvk.leh.seabuckthorn.domain.model.SoilMoistureClass.DRY,
        waterRegime = env?.waterRegime?.let { runCatching { com.kvk.leh.seabuckthorn.domain.model.WaterRegime.valueOf(it) }.getOrNull() } ?: com.kvk.leh.seabuckthorn.domain.model.WaterRegime.NATURAL,
        distanceFromWaterM = env?.distanceFromWaterM,
        associatedVegetation = env?.associatedVegetation ?: "",
        grazingIntensity = env?.grazingIntensity?.let { runCatching { com.kvk.leh.seabuckthorn.domain.model.Level4.valueOf(it) }.getOrNull() } ?: com.kvk.leh.seabuckthorn.domain.model.Level4.NONE,
        riverName = env?.riverName ?: "",
        elevationZone = env?.elevationZone ?: "",
        terrainType = env?.terrainType?.let { runCatching { com.kvk.leh.seabuckthorn.domain.model.TerrainType.valueOf(it) }.getOrNull() } ?: com.kvk.leh.seabuckthorn.domain.model.TerrainType.RIVER_TERRACE,
        soilType = env?.soilType ?: "",
        soilSurfaceTexture = env?.soilSurfaceTexture ?: "",
        landUseHistory = env?.landUseHistory ?: "",
        photos = photos
    )
}

fun SurveyFormState.toSurveyRecord(isDraft: Boolean): SurveyRecord {
    val now = Instant.now().toEpochMilli()
    val avgDiameter = CanopyCalculations.averageCanopyDiameter(canopyNsM, canopyEwM)
    val canopyArea = CanopyCalculations.canopyArea(avgDiameter)

    val survey = SurveyEntity(
        id = surveyId,
        surveyCode = surveyCode,
        createdAt = now,
        updatedAt = now,
        surveyDateEpochDay = surveyDateEpochDay,
        surveyTime = surveyTime,
        surveyorName = surveyorName,
        district = district,
        block = block,
        village = village,
        siteName = siteName,
        landUseType = landUseType.name,
        landUseOther = landUseOther.ifBlank { null },
        ownershipStatus = ownershipStatus.name,
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,
        gpsAccuracyM = gpsAccuracyM,
        gpsAcquiredAt = gpsAcquiredAt,
        gpsStatus = gpsStatus.name,
        remarks = remarks.ifBlank { null },
        isDraft = isDraft
    )
    val shrub = ShrubEntity(
        surveyId = surveyId,
        shrubType = shrubType.name,
        growthForm = growthForm.name,
        plantHeightM = plantHeightM,
        canopyNsM = canopyNsM,
        canopyEwM = canopyEwM,
        avgCanopyDiameterM = avgDiameter,
        canopyAreaM2 = canopyArea,
        stemCircumferenceCm = stemCircumferenceCm,
        numMajorStems = numMajorStems?.toInt(),
        estimatedAgeYears = estimatedAgeYears,
        ageEstimationMethod = ageEstimationMethod.ifBlank { null },
        plantDensityPerHa = plantDensityPerHa,
        approxSpacingM = approxSpacingM,
        regenerationStatus = regenerationStatus.name,
        presenceOfSuckers = presenceOfSuckers,
        floweringStatus = floweringStatus,
        fruitingStatus = fruitingStatus,
        numBranches = numBranches?.toInt(),
        mainStemDiameterCm = mainStemDiameterCm,
        branchDiameterCm = branchDiameterCm,
        leafColor = leafColor.ifBlank { null },
        leafDensity = leafDensity.name,
        canopyCoverPercent = canopyCoverPercent,
        thornDensity = thornDensity.name,
        suckerAbundance = suckerAbundance.name,
        overallHealth = overallHealth.name,
        pestIncidence = pestIncidence.name,
        diseaseIncidence = diseaseIncidence.name,
        browsingDamage = browsingDamage.name,
        mechanicalDamage = mechanicalDamage.name,
        droughtStress = droughtStress.name,
        otherStressSymptoms = otherStressSymptoms.ifBlank { null }
    )
    val phenology = PhenologyEntity(
        surveyId = surveyId,
        dominantMaturityStage = dominantMaturityStage?.name,
        pctUnripe = pctUnripe, pctIntermediate = pctIntermediate, pctRipe = pctRipe, pctOverripe = pctOverripe,
        floweringInitiationDate = floweringInitiationDate.epochMillis, floweringInitiationEstimated = floweringInitiationDate.isEstimated,
        floweringPeakDate = floweringPeakDate.epochMillis, floweringPeakEstimated = floweringPeakDate.isEstimated,
        fruitSetDate = fruitSetDate.epochMillis, fruitSetEstimated = fruitSetDate.isEstimated,
        fruitDevelopmentInitiationDate = fruitDevelopmentInitiationDate.epochMillis, fruitDevelopmentInitiationEstimated = fruitDevelopmentInitiationDate.isEstimated,
        firstColorChangeDate = firstColorChangeDate.epochMillis, firstColorChangeEstimated = firstColorChangeDate.isEstimated,
        firstMaturityDate = firstMaturityDate.epochMillis, firstMaturityEstimated = firstMaturityDate.isEstimated,
        fiftyPercentMaturityDate = fiftyPercentMaturityDate.epochMillis, fiftyPercentMaturityEstimated = fiftyPercentMaturityDate.isEstimated,
        peakMaturityDate = peakMaturityDate.epochMillis, peakMaturityEstimated = peakMaturityDate.isEstimated,
        harvestInitiationDate = harvestInitiationDate.epochMillis, harvestInitiationEstimated = harvestInitiationDate.isEstimated,
        estimatedFullMaturityDate = estimatedFullMaturityDate.epochMillis, estimatedFullMaturityEstimated = estimatedFullMaturityDate.isEstimated
    )
    val berryEntities = berries.filter { it.lengthMm != null || it.widthMm != null || it.weightG != null }
        .map { BerryMeasurementEntity(surveyId = surveyId, berryIndex = it.index, lengthMm = it.lengthMm, widthMm = it.widthMm, weightG = it.weightG) }
    val fruitQuality = FruitQualityEntity(
        surveyId = surveyId,
        fruitColor = fruitColor.name,
        fruitColorOther = fruitColorOther.ifBlank { null },
        fruitFirmness = fruitFirmness.name,
        fruitShape = fruitShape.name,
        fruitShapeOther = fruitShapeOther.ifBlank { null },
        berriesPerCluster = berriesPerCluster,
        tssBrix = tssBrix,
        ph = ph,
        juiceYieldPercent = juiceYieldPercent,
        titratableAcidityPercent = titratableAcidityPercent,
        vitaminCMgPer100g = vitaminCMgPer100g,
        totalCarotenoidsMgPer100g = totalCarotenoidsMgPer100g,
        otherLabParams = otherLabParams.ifBlank { null },
        fruitsPerBranch = fruitsPerBranch,
        fruitsPerCluster = fruitsPerCluster,
        estimatedYieldKgPerShrub = estimatedYieldKgPerShrub,
        fruitBearingBranchPercent = fruitBearingBranchPercent,
        berryColorIntensity = berryColorIntensity.name,
        berryDetachmentEase = berryDetachmentEase.name,
        fruitDamagePercent = fruitDamagePercent
    )
    val environment = EnvironmentEntity(
        surveyId = surveyId,
        slopePercent = slopePercent,
        aspect = aspect.name,
        soilSurfaceCondition = soilSurfaceCondition.ifBlank { null },
        soilMoistureClass = soilMoistureClass.name,
        waterRegime = waterRegime.name,
        distanceFromWaterM = distanceFromWaterM,
        associatedVegetation = associatedVegetation.ifBlank { null },
        grazingIntensity = grazingIntensity.name,
        riverName = riverName.ifBlank { null },
        elevationZone = elevationZone.ifBlank { null },
        terrainType = terrainType.name,
        soilType = soilType.ifBlank { null },
        soilSurfaceTexture = soilSurfaceTexture.ifBlank { null },
        landUseHistory = landUseHistory.ifBlank { null }
    )
    return SurveyRecord(survey, shrub, phenology, berryEntities, fruitQuality, environment, photos)
}
