package com.kvkleh.sbtsurvey.data.repo

import com.kvkleh.sbtsurvey.data.db.SurveyDao
import com.kvkleh.sbtsurvey.data.db.SurveyEntity
import com.kvkleh.sbtsurvey.data.photo.PhotoStore
import com.kvkleh.sbtsurvey.data.prefs.LastLocation
import com.kvkleh.sbtsurvey.data.prefs.SurveyPreferences
import com.kvkleh.sbtsurvey.data.prefs.SurveyorProfile
import kotlinx.coroutines.flow.Flow

/**
 * Single entry point to survey storage.
 *
 * Records are written continuously as drafts and only flipped to `SAVED` once the
 * surveyor confirms on the review screen; nothing is ever removed without an
 * explicit request.
 */
class SurveyRepository(
    private val dao: SurveyDao,
    private val photoStore: PhotoStore,
    private val preferences: SurveyPreferences
) {

    fun observeSaved(): Flow<List<SurveyEntity>> = dao.observeSaved()

    fun observeSavedCount(): Flow<Int> = dao.observeSavedCount()

    fun observeDraft(): Flow<SurveyEntity?> = dao.observeLatestDraft()

    fun observeById(id: Long): Flow<SurveyEntity?> = dao.observeById(id)

    fun observeKnownVillages(): Flow<List<String>> = dao.observeKnownVillages()

    suspend fun getById(id: Long): SurveyEntity? = dao.getById(id)

    suspend fun getLatestDraft(): SurveyEntity? = dao.getLatestDraft()

    suspend fun getAllForExport(): List<SurveyEntity> = dao.getAllForExport()

    /**
     * Creates and immediately persists a new draft, pre-filled with the surveyor
     * profile and the last district/block used. Returns the new row id.
     */
    suspend fun startDraft(
        profile: SurveyorProfile,
        lastLocation: LastLocation,
        now: Long = System.currentTimeMillis()
    ): Long {
        val template = SurveyEntity(
            surveyorName = profile.name,
            designation = profile.designation,
            organization = profile.organization,
            district = lastLocation.district,
            block = lastLocation.block,
            village = lastLocation.village,
            site = lastLocation.site
        )
        return dao.insertWithNewSurveyId(template, now)
    }

    /** Persists the in-progress form. Called by the auto-save loop. */
    suspend fun saveDraft(survey: SurveyEntity, now: Long = System.currentTimeMillis()) {
        dao.update(survey.copy(updatedAt = now))
    }

    /**
     * Promotes a draft to a saved record. Also refreshes the remembered surveyor
     * and location so the next survey starts pre-filled.
     */
    suspend fun commit(survey: SurveyEntity, now: Long = System.currentTimeMillis()): SurveyEntity {
        val committed = survey.copy(
            status = SurveyEntity.STATUS_SAVED,
            updatedAt = now,
            savedAt = survey.savedAt ?: now
        )
        dao.update(committed)
        preferences.saveProfile(
            SurveyorProfile(
                name = committed.surveyorName,
                designation = committed.designation,
                organization = committed.organization
            )
        )
        preferences.saveLastLocation(
            LastLocation(
                district = committed.district,
                block = committed.block,
                village = committed.village,
                site = committed.site
            )
        )
        return committed
    }

    /** Links a captured photo to the record. */
    suspend fun attachPhoto(id: Long, absolutePath: String, fileName: String) {
        val survey = dao.getById(id) ?: return
        dao.update(
            survey.copy(
                photoPath = absolutePath,
                photoFileName = fileName,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Removes the image file for a record. The row itself is updated by the caller
     * in the same breath, so a pending auto-save can never re-attach a file that
     * has just been deleted.
     */
    fun deletePhotoFile(surveyId: String) {
        photoStore.delete(surveyId)
    }

    /** Deletes a record and its photo. Only ever called behind a confirmation dialog. */
    suspend fun delete(survey: SurveyEntity) {
        photoStore.delete(survey.surveyId)
        dao.delete(survey)
    }
}
