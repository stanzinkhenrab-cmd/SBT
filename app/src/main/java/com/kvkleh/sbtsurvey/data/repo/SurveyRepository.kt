package com.kvkleh.sbtsurvey.data.repo

import android.content.Context
import com.kvkleh.sbtsurvey.data.local.AppDatabase
import com.kvkleh.sbtsurvey.data.local.SurveyDao
import com.kvkleh.sbtsurvey.data.local.SurveyEntity
import com.kvkleh.sbtsurvey.domain.SurveyStatus
import com.kvkleh.sbtsurvey.photo.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Single entry point to survey storage.
 *
 * All writes go through here so that Survey ID allocation, timestamps and photograph
 * lifecycle stay consistent, and so the rest of the app never touches Room directly.
 */
class SurveyRepository(
    private val dao: SurveyDao,
    private val photoStore: PhotoStore,
    private val context: Context
) {

    fun observeAll(): Flow<List<SurveyEntity>> = dao.observeAll()

    fun observeCompleted(): Flow<List<SurveyEntity>> =
        dao.observeByStatus(SurveyStatus.COMPLETED.storageValue)

    fun observeWithLocation(): Flow<List<SurveyEntity>> = dao.observeWithLocation()

    fun observeById(id: Long): Flow<SurveyEntity?> = dao.observeById(id)

    suspend fun getById(id: Long): SurveyEntity? = dao.getById(id)

    suspend fun getAllOnce(): List<SurveyEntity> = dao.getAllOnce()

    suspend fun latestDraft(): SurveyEntity? = dao.latestDraft()

    /**
     * Creates a new draft record with a freshly allocated Survey ID and today's date/time,
     * pre-filling the surveyor identity from the most recent record.
     *
     * @return the row id of the new draft.
     */
    suspend fun createDraft(): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val previous = dao.mostRecent()
        val template = SurveyEntity(
            surveyId = "",  // replaced inside the allocating transaction
            surveyorName = previous?.surveyorName.orEmpty(),
            designation = previous?.designation.orEmpty(),
            organization = previous?.organization ?: SurveyEntity.DEFAULT_ORGANIZATION,
            date = formatDate(now),
            time = formatTime(now),
            district = previous?.district.orEmpty(),
            block = previous?.block.orEmpty(),
            createdAt = now,
            updatedAt = now,
            status = SurveyStatus.DRAFT.storageValue
        )
        dao.insertWithNewSurveyId(currentYear(), template)
    }

    /** Persists an in-progress edit. Stamps [SurveyEntity.updatedAt] for the caller. */
    suspend fun save(survey: SurveyEntity) = withContext(Dispatchers.IO) {
        dao.update(survey.copy(updatedAt = System.currentTimeMillis()))
    }

    /** Marks a draft complete. */
    suspend fun complete(survey: SurveyEntity) = withContext(Dispatchers.IO) {
        dao.update(
            survey.copy(
                status = SurveyStatus.COMPLETED.storageValue,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** Deletes a record together with its photograph. */
    suspend fun delete(survey: SurveyEntity) = withContext(Dispatchers.IO) {
        photoStore.delete(survey.photoPath)
        dao.deleteById(survey.id)
    }

    /**
     * Replaces the photograph on a record, removing the previous file.
     * Passing a null [file] clears the photograph.
     */
    suspend fun setPhoto(survey: SurveyEntity, file: File?, capturedAt: Long?): SurveyEntity =
        withContext(Dispatchers.IO) {
            if (survey.photoPath != null && survey.photoPath != file?.absolutePath) {
                photoStore.delete(survey.photoPath)
            }
            val updated = survey.copy(
                photoPath = file?.absolutePath,
                photoFileName = file?.name,
                photoCapturedAt = if (file == null) null else (capturedAt ?: System.currentTimeMillis()),
                updatedAt = System.currentTimeMillis()
            )
            dao.update(updated)
            updated
        }

    /**
     * Copies the database to a timestamped file in app-private storage.
     *
     * Cheap insurance against database corruption in the field. The newest
     * [MAX_BACKUPS] copies are kept.
     */
    suspend fun backupDatabase(): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dbFile = context.getDatabasePath("sbt_survey.db")
            if (!dbFile.exists()) return@runCatching null
            // Flush WAL content into the main database file first.
            AppDatabase.get(context).openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }

            val dir = File(context.filesDir, BACKUP_DIR).apply { mkdirs() }
            val target = File(dir, "sbt_survey_${formatFileStamp(System.currentTimeMillis())}.db")
            dbFile.copyTo(target, overwrite = true)

            dir.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_BACKUPS)
                ?.forEach { it.delete() }
            target
        }.getOrNull()
    }

    companion object {
        private const val BACKUP_DIR = "backups"
        private const val MAX_BACKUPS = 5

        fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

        fun formatDate(millis: Long): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

        fun formatTime(millis: Long): String =
            SimpleDateFormat("HH:mm", Locale.US).format(Date(millis))

        fun formatFileStamp(millis: Long): String =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(millis))
    }
}
