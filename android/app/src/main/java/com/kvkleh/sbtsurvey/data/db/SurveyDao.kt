package com.kvkleh.sbtsurvey.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.Locale

@Dao
abstract class SurveyDao {

    @Insert
    abstract suspend fun insert(survey: SurveyEntity): Long

    @Update
    abstract suspend fun update(survey: SurveyEntity)

    @Delete
    abstract suspend fun delete(survey: SurveyEntity)

    @Query("SELECT * FROM surveys WHERE id = :id")
    abstract suspend fun getById(id: Long): SurveyEntity?

    @Query("SELECT * FROM surveys WHERE id = :id")
    abstract fun observeById(id: Long): Flow<SurveyEntity?>

    @Query("SELECT * FROM surveys WHERE surveyId = :surveyId LIMIT 1")
    abstract suspend fun getBySurveyId(surveyId: String): SurveyEntity?

    @Query(
        "SELECT * FROM surveys WHERE status = '${SurveyEntity.STATUS_SAVED}' " +
            "ORDER BY createdAt DESC, id DESC"
    )
    abstract fun observeSaved(): Flow<List<SurveyEntity>>

    @Query("SELECT COUNT(*) FROM surveys WHERE status = '${SurveyEntity.STATUS_SAVED}'")
    abstract fun observeSavedCount(): Flow<Int>

    @Query(
        "SELECT * FROM surveys WHERE status = '${SurveyEntity.STATUS_DRAFT}' " +
            "ORDER BY updatedAt DESC LIMIT 1"
    )
    abstract fun observeLatestDraft(): Flow<SurveyEntity?>

    @Query(
        "SELECT * FROM surveys WHERE status = '${SurveyEntity.STATUS_DRAFT}' " +
            "ORDER BY updatedAt DESC LIMIT 1"
    )
    abstract suspend fun getLatestDraft(): SurveyEntity?

    @Query("SELECT * FROM surveys ORDER BY createdAt DESC, id DESC")
    abstract suspend fun getAllForExport(): List<SurveyEntity>

    @Query(
        "SELECT DISTINCT village FROM surveys WHERE village <> '' " +
            "ORDER BY village COLLATE NOCASE"
    )
    abstract fun observeKnownVillages(): Flow<List<String>>

    @Query("SELECT lastSequence FROM id_counter WHERE year = :year")
    abstract suspend fun getSequence(year: Int): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertCounter(counter: IdCounterEntity)

    /**
     * Allocates the next free identifier for the current year and inserts [template]
     * with it, in a single transaction, so two rapid taps on "New Survey" can never
     * produce the same [SurveyEntity.surveyId].
     */
    @Transaction
    open suspend fun insertWithNewSurveyId(template: SurveyEntity, now: Long): Long {
        val year = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
        var sequence = (getSequence(year) ?: 0) + 1
        var candidate = formatSurveyId(year, sequence)
        // Defensive: an imported or restored database may already hold identifiers
        // beyond the counter. Skip over anything that is taken rather than failing.
        while (getBySurveyId(candidate) != null) {
            sequence++
            candidate = formatSurveyId(year, sequence)
        }
        upsertCounter(IdCounterEntity(year, sequence))
        return insert(
            template.copy(
                surveyId = candidate,
                status = SurveyEntity.STATUS_DRAFT,
                createdAt = now,
                updatedAt = now,
                savedAt = null
            )
        )
    }

    companion object {
        fun formatSurveyId(year: Int, sequence: Int): String =
            String.format(Locale.US, "SBT-%04d-%04d", year, sequence)
    }
}
