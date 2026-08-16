package com.kvk.leh.seabuckthorn.domain.model

import com.kvk.leh.seabuckthorn.data.local.entity.BerryMeasurementEntity
import com.kvk.leh.seabuckthorn.data.local.entity.EnvironmentEntity
import com.kvk.leh.seabuckthorn.data.local.entity.FruitQualityEntity
import com.kvk.leh.seabuckthorn.data.local.entity.PhenologyEntity
import com.kvk.leh.seabuckthorn.data.local.entity.PhotoEntity
import com.kvk.leh.seabuckthorn.data.local.entity.ShrubEntity
import com.kvk.leh.seabuckthorn.data.local.entity.SurveyEntity

/**
 * A full survey with all of its related child records assembled together — the unit the wizard,
 * review screen, detail screen and exporters all operate on.
 */
data class SurveyRecord(
    val survey: SurveyEntity,
    val shrub: ShrubEntity?,
    val phenology: PhenologyEntity?,
    val berries: List<BerryMeasurementEntity>,
    val fruitQuality: FruitQualityEntity?,
    val environment: EnvironmentEntity?,
    val photos: List<PhotoEntity>
)

data class DashboardStats(
    val totalSurveys: Int,
    val totalShrubs: Int,
    val ripeObservations: Int,
    val intermediateObservations: Int,
    val unripeObservations: Int,
    val overripeObservations: Int,
    val villagesSurveyed: Int,
    val averageTssBrix: Double?,
    val averageBerryLengthMm: Double?,
    val latestSurvey: SurveyEntity?,
    val maturityDistribution: Map<MaturityStage, Int>,
    val tssDistributionBuckets: Map<String, Int>,
    val berrySizeDistributionBuckets: Map<String, Int>,
    val surveysByDate: List<Pair<Long, Int>>,
    val surveysByVillage: List<Pair<String, Int>>
)
