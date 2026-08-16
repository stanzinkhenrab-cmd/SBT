package com.kvk.leh.seabuckthorn.ui.survey.wizard

import com.kvk.leh.seabuckthorn.data.local.entity.PhotoEntity
import com.kvk.leh.seabuckthorn.domain.model.*
import com.kvk.leh.seabuckthorn.util.DateUtils

/** All editable state for one in-progress survey, mirroring (but not identical to) the Room entities. */
data class SurveyFormState(
    val surveyId: String = "",
    val surveyCode: String = "",
    val isDraft: Boolean = true,
    val isEditingExisting: Boolean = false,

    // Step 1 — Survey Information
    val surveyDateEpochDay: Long = DateUtils.todayEpochDay(),
    val surveyTime: String = DateUtils.nowTimeString(),
    val surveyorName: String = "",
    val district: String = "Leh",
    val block: String = "",
    val village: String = "",
    val siteName: String = "",
    val landUseType: LandUseType = LandUseType.NATURAL_STAND,
    val landUseOther: String = "",
    val ownershipStatus: OwnershipStatus = OwnershipStatus.UNKNOWN,
    val remarks: String = "",

    // Step 2 — Location
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val gpsAccuracyM: Float? = null,
    val gpsAcquiredAt: Long? = null,
    val gpsStatus: GpsStatus = GpsStatus.UNAVAILABLE,

    // Step 3 — Shrub characteristics
    val shrubType: ShrubType = ShrubType.UNKNOWN,
    val growthForm: GrowthForm = GrowthForm.ERECT,
    val plantHeightM: Double? = null,
    val canopyNsM: Double? = null,
    val canopyEwM: Double? = null,
    val stemCircumferenceCm: Double? = null,
    val numMajorStems: Double? = null,
    val estimatedAgeYears: Double? = null,
    val ageEstimationMethod: String = "",
    val plantDensityPerHa: Double? = null,
    val approxSpacingM: Double? = null,
    val regenerationStatus: RegenerationStatus = RegenerationStatus.MODERATE,
    val presenceOfSuckers: Boolean = false,
    val floweringStatus: Boolean = false,
    val fruitingStatus: Boolean = false,
    val numBranches: Double? = null,
    val mainStemDiameterCm: Double? = null,
    val branchDiameterCm: Double? = null,
    val leafColor: String = "",
    val leafDensity: Level4 = Level4.MODERATE,
    val canopyCoverPercent: Double? = null,
    val thornDensity: Level4 = Level4.MODERATE,
    val suckerAbundance: Level4 = Level4.NONE,
    val overallHealth: HealthStatus = HealthStatus.GOOD,
    val pestIncidence: Level4 = Level4.NONE,
    val diseaseIncidence: Level4 = Level4.NONE,
    val browsingDamage: Level4 = Level4.NONE,
    val mechanicalDamage: Level4 = Level4.NONE,
    val droughtStress: Level4 = Level4.NONE,
    val otherStressSymptoms: String = "",

    // Step 4 — Phenology / Maturity
    val dominantMaturityStage: MaturityStage? = null,
    val pctUnripe: Double? = null,
    val pctIntermediate: Double? = null,
    val pctRipe: Double? = null,
    val pctOverripe: Double? = null,
    val floweringInitiationDate: PhenoDate = PhenoDate(),
    val floweringPeakDate: PhenoDate = PhenoDate(),
    val fruitSetDate: PhenoDate = PhenoDate(),
    val fruitDevelopmentInitiationDate: PhenoDate = PhenoDate(),
    val firstColorChangeDate: PhenoDate = PhenoDate(),
    val firstMaturityDate: PhenoDate = PhenoDate(),
    val fiftyPercentMaturityDate: PhenoDate = PhenoDate(),
    val peakMaturityDate: PhenoDate = PhenoDate(),
    val harvestInitiationDate: PhenoDate = PhenoDate(),
    val estimatedFullMaturityDate: PhenoDate = PhenoDate(),

    // Step 5 — Berry measurements
    val berries: List<BerryRow> = listOf(BerryRow(1)),

    // Step 6a — Fruit quality
    val fruitColor: FruitColor = FruitColor.ORANGE,
    val fruitColorOther: String = "",
    val fruitFirmness: FruitFirmness = FruitFirmness.MODERATE,
    val fruitShape: FruitShape = FruitShape.ROUND,
    val fruitShapeOther: String = "",
    val berriesPerCluster: Double? = null,
    val tssBrix: Double? = null,
    val ph: Double? = null,
    val juiceYieldPercent: Double? = null,
    val titratableAcidityPercent: Double? = null,
    val vitaminCMgPer100g: Double? = null,
    val totalCarotenoidsMgPer100g: Double? = null,
    val otherLabParams: String = "",
    val fruitsPerBranch: Double? = null,
    val fruitsPerCluster: Double? = null,
    val estimatedYieldKgPerShrub: Double? = null,
    val fruitBearingBranchPercent: Double? = null,
    val berryColorIntensity: ColorIntensity = ColorIntensity.MEDIUM,
    val berryDetachmentEase: EaseLevel = EaseLevel.MODERATE,
    val fruitDamagePercent: Double? = null,

    // Step 6b — Environmental parameters
    val slopePercent: Double? = null,
    val aspect: Aspect = Aspect.FLAT,
    val soilSurfaceCondition: String = "",
    val soilMoistureClass: SoilMoistureClass = SoilMoistureClass.DRY,
    val waterRegime: WaterRegime = WaterRegime.NATURAL,
    val distanceFromWaterM: Double? = null,
    val associatedVegetation: String = "",
    val grazingIntensity: Level4 = Level4.NONE,
    val riverName: String = "",
    val elevationZone: String = "",
    val terrainType: TerrainType = TerrainType.RIVER_TERRACE,
    val soilType: String = "",
    val soilSurfaceTexture: String = "",
    val landUseHistory: String = "",

    // Step 7 — Photos
    val photos: List<PhotoEntity> = emptyList()
) {
    val maturityPercentTotal: Double
        get() = listOfNotNull(pctUnripe, pctIntermediate, pctRipe, pctOverripe).sum()
}

data class PhenoDate(val epochMillis: Long? = null, val isEstimated: Boolean = true)

data class BerryRow(
    val index: Int,
    val lengthMm: Double? = null,
    val widthMm: Double? = null,
    val weightG: Double? = null
)
