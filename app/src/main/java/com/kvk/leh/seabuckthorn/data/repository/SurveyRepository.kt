package com.kvk.leh.seabuckthorn.data.repository

import androidx.room.withTransaction
import com.kvk.leh.seabuckthorn.data.local.AppDatabase
import com.kvk.leh.seabuckthorn.data.local.entity.PhotoEntity
import com.kvk.leh.seabuckthorn.data.photo.PhotoStorage
import com.kvk.leh.seabuckthorn.domain.calculations.DescriptiveStatistics
import com.kvk.leh.seabuckthorn.domain.model.DashboardStats
import com.kvk.leh.seabuckthorn.domain.model.MaturityStage
import com.kvk.leh.seabuckthorn.domain.model.SurveyRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId

/**
 * Single entry point for all survey persistence. Every multi-table write goes through a Room
 * transaction so a survey and its shrub/phenology/fruit/environment/photo children are always
 * consistent, even if the app is killed mid-save.
 */
class SurveyRepository(private val db: AppDatabase) {

    private val surveyDao = db.surveyDao()
    private val shrubDao = db.shrubDao()
    private val phenologyDao = db.phenologyDao()
    private val berryDao = db.berryMeasurementDao()
    private val fruitQualityDao = db.fruitQualityDao()
    private val environmentDao = db.environmentDao()
    private val photoDao = db.photoDao()

    fun observeSurveyList(): Flow<List<com.kvk.leh.seabuckthorn.data.local.entity.SurveyEntity>> =
        surveyDao.observeAll()

    fun observeSurveysWithGps(): Flow<List<com.kvk.leh.seabuckthorn.data.local.entity.SurveyEntity>> =
        surveyDao.observeAllWithGps()

    fun search(
        query: String = "",
        village: String = "",
        surveyor: String = "",
        maturityStage: String = "",
        fromEpochDay: Long? = null,
        toEpochDay: Long? = null
    ) = surveyDao.search(query, village, surveyor, maturityStage, fromEpochDay, toEpochDay)

    fun observeDistinctVillages(): Flow<List<String>> = surveyDao.observeDistinctVillages()
    fun observeDistinctSurveyors(): Flow<List<String>> = surveyDao.observeDistinctSurveyors()

    suspend fun getSurveyRecord(surveyId: String): SurveyRecord? {
        val survey = surveyDao.getById(surveyId) ?: return null
        return SurveyRecord(
            survey = survey,
            shrub = shrubDao.getBySurveyId(surveyId),
            phenology = phenologyDao.getBySurveyId(surveyId),
            berries = berryDao.getBySurveyId(surveyId),
            fruitQuality = fruitQualityDao.getBySurveyId(surveyId),
            environment = environmentDao.getBySurveyId(surveyId),
            photos = photoDao.getBySurveyId(surveyId)
        )
    }

    fun observePhotos(surveyId: String): Flow<List<PhotoEntity>> = photoDao.observeBySurveyId(surveyId)

    /** Persists a complete survey record atomically. Safe to call repeatedly (upsert semantics). */
    suspend fun saveSurveyRecord(record: SurveyRecord) {
        db.withTransaction {
            surveyDao.upsert(record.survey)
            record.shrub?.let { shrubDao.upsert(it) }
            record.phenology?.let { phenologyDao.upsert(it) }
            berryDao.deleteBySurveyId(record.survey.id)
            if (record.berries.isNotEmpty()) {
                berryDao.insertAll(record.berries.map { it.copy(surveyId = record.survey.id) })
            }
            record.fruitQuality?.let { fruitQualityDao.upsert(it) }
            record.environment?.let { environmentDao.upsert(it) }
        }
    }

    suspend fun addPhoto(photo: PhotoEntity): Long = photoDao.insert(photo)

    suspend fun deletePhoto(photo: PhotoEntity) {
        photoDao.deleteById(photo.id)
        PhotoStorage.deleteFile(photo.filePath)
    }

    suspend fun nextPhotoNumber(surveyId: String): Int = (photoDao.maxPhotoNumber(surveyId) ?: 0) + 1

    /** Deletes a survey and all related rows (cascade) plus its photo files on disk. */
    suspend fun deleteSurvey(surveyId: String) {
        val photos = photoDao.getBySurveyId(surveyId)
        db.withTransaction {
            surveyDao.deleteById(surveyId)
        }
        photos.forEach { PhotoStorage.deleteFile(it.filePath) }
    }

    /**
     * Creates a new draft survey pre-filled from an existing one (shrub/phenology/fruit/environment
     * carried over) but with a fresh ID/code, today's date, and cleared GPS — since a duplicate
     * represents a new field visit that must capture its own location.
     */
    suspend fun duplicateSurvey(surveyId: String, newSurveyId: String, newSurveyCode: String): SurveyRecord? {
        val source = getSurveyRecord(surveyId) ?: return null
        val today = Instant.now()
        val duplicated = source.copy(
            survey = source.survey.copy(
                id = newSurveyId,
                surveyCode = newSurveyCode,
                createdAt = today.toEpochMilli(),
                updatedAt = today.toEpochMilli(),
                surveyDateEpochDay = today.atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay(),
                latitude = null,
                longitude = null,
                altitude = null,
                gpsAccuracyM = null,
                gpsAcquiredAt = null,
                gpsStatus = com.kvk.leh.seabuckthorn.domain.model.GpsStatus.UNAVAILABLE.name,
                isDraft = true
            ),
            shrub = source.shrub?.copy(surveyId = newSurveyId),
            phenology = source.phenology?.copy(surveyId = newSurveyId),
            berries = source.berries.map { it.copy(id = 0, surveyId = newSurveyId) },
            fruitQuality = source.fruitQuality?.copy(surveyId = newSurveyId),
            environment = source.environment?.copy(surveyId = newSurveyId),
            photos = emptyList()
        )
        saveSurveyRecord(duplicated)
        return duplicated
    }

    suspend fun getAllRecordsForExport(): List<SurveyRecord> =
        surveyDao.getAllCompleted().map { getSurveyRecord(it.id)!! }

    suspend fun getDashboardStats(): DashboardStats {
        val allSurveys = surveyDao.getAllCompleted()
        val totalSurveys = allSurveys.size
        val villages = allSurveys.map { it.village }.filter { it.isNotBlank() }.distinct().size

        var ripe = 0; var intermediate = 0; var unripe = 0; var overripe = 0
        val maturityDistribution = linkedMapOf(
            MaturityStage.UNRIPE to 0, MaturityStage.INTERMEDIATE to 0,
            MaturityStage.RIPE to 0, MaturityStage.OVERRIPE to 0
        )
        for (survey in allSurveys) {
            val phenology = phenologyDao.getBySurveyId(survey.id) ?: continue
            when (phenology.dominantMaturityStage) {
                MaturityStage.RIPE.name -> { ripe++; maturityDistribution[MaturityStage.RIPE] = maturityDistribution.getValue(MaturityStage.RIPE) + 1 }
                MaturityStage.INTERMEDIATE.name -> { intermediate++; maturityDistribution[MaturityStage.INTERMEDIATE] = maturityDistribution.getValue(MaturityStage.INTERMEDIATE) + 1 }
                MaturityStage.UNRIPE.name -> { unripe++; maturityDistribution[MaturityStage.UNRIPE] = maturityDistribution.getValue(MaturityStage.UNRIPE) + 1 }
                MaturityStage.OVERRIPE.name -> { overripe++; maturityDistribution[MaturityStage.OVERRIPE] = maturityDistribution.getValue(MaturityStage.OVERRIPE) + 1 }
            }
        }

        val tssValues = fruitQualityDao.allTssValues()
        val avgTss = if (tssValues.isNotEmpty()) tssValues.average() else null
        val tssBuckets = bucketize(tssValues, listOf(0.0, 8.0, 12.0, 16.0, 20.0, 100.0),
            listOf("<8", "8-12", "12-16", "16-20", "20+"))

        val lengths = berryDao.allLengths()
        val avgLength = if (lengths.isNotEmpty()) lengths.average() else null
        val berryBuckets = bucketize(lengths, listOf(0.0, 6.0, 8.0, 10.0, 12.0, 100.0),
            listOf("<6mm", "6-8mm", "8-10mm", "10-12mm", "12mm+"))

        val latest = surveyDao.getLatest()

        val surveysByDate = allSurveys.groupBy { it.surveyDateEpochDay }
            .map { (day, list) -> day to list.size }
            .sortedBy { it.first }
        val surveysByVillage = allSurveys.filter { it.village.isNotBlank() }
            .groupBy { it.village }
            .map { (village, list) -> village to list.size }
            .sortedByDescending { it.second }

        return DashboardStats(
            totalSurveys = totalSurveys,
            totalShrubs = totalSurveys,
            ripeObservations = ripe,
            intermediateObservations = intermediate,
            unripeObservations = unripe,
            overripeObservations = overripe,
            villagesSurveyed = villages,
            averageTssBrix = avgTss,
            averageBerryLengthMm = avgLength,
            latestSurvey = latest,
            maturityDistribution = maturityDistribution,
            tssDistributionBuckets = tssBuckets,
            berrySizeDistributionBuckets = berryBuckets,
            surveysByDate = surveysByDate,
            surveysByVillage = surveysByVillage
        )
    }

    private fun bucketize(values: List<Double>, edges: List<Double>, labels: List<String>): Map<String, Int> {
        val result = linkedMapOf<String, Int>()
        labels.forEach { result[it] = 0 }
        for (value in values) {
            for (i in labels.indices) {
                val lower = edges[i]
                val upper = edges[i + 1]
                val inBucket = if (i == labels.lastIndex) value >= lower else value >= lower && value < upper
                if (inBucket) {
                    result[labels[i]] = result.getValue(labels[i]) + 1
                    break
                }
            }
        }
        return result
    }
}
