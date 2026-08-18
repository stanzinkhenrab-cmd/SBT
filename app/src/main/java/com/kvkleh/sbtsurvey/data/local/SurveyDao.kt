package com.kvkleh.sbtsurvey.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data access for survey records.
 *
 * Declared as an abstract class rather than an interface so that
 * [insertWithNewSurveyId] can carry a real body inside a Room `@Transaction`.
 */
@Dao
abstract class SurveyDao {

    // --- Reads ----------------------------------------------------------------

    @Query("SELECT * FROM surveys ORDER BY created_at DESC")
    abstract fun observeAll(): Flow<List<SurveyEntity>>

    @Query("SELECT * FROM surveys WHERE status = :status ORDER BY created_at DESC")
    abstract fun observeByStatus(status: String): Flow<List<SurveyEntity>>

    @Query("SELECT * FROM surveys WHERE id = :id")
    abstract fun observeById(id: Long): Flow<SurveyEntity?>

    @Query("SELECT * FROM surveys WHERE id = :id")
    abstract suspend fun getById(id: Long): SurveyEntity?

    @Query("SELECT * FROM surveys ORDER BY created_at ASC")
    abstract suspend fun getAllOnce(): List<SurveyEntity>

    @Query(
        "SELECT * FROM surveys WHERE latitude IS NOT NULL AND longitude IS NOT NULL " +
            "ORDER BY created_at DESC"
    )
    abstract fun observeWithLocation(): Flow<List<SurveyEntity>>

    @Query("SELECT COUNT(*) FROM surveys WHERE status = :status")
    abstract fun countByStatus(status: String): Flow<Int>

    /** Most recently touched draft, used for "recover unfinished survey". */
    @Query("SELECT * FROM surveys WHERE status = 'draft' ORDER BY updated_at DESC LIMIT 1")
    abstract suspend fun latestDraft(): SurveyEntity?

    /** Surveyor identity of the last saved record, reused to pre-fill the next survey. */
    @Query("SELECT * FROM surveys ORDER BY updated_at DESC LIMIT 1")
    abstract suspend fun mostRecent(): SurveyEntity?

    // --- Writes ---------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(survey: SurveyEntity): Long

    @Update
    abstract suspend fun update(survey: SurveyEntity)

    @Query("DELETE FROM surveys WHERE id = :id")
    abstract suspend fun deleteById(id: Long)

    @Query("UPDATE surveys SET status = :status, updated_at = :updatedAt WHERE id = :id")
    abstract suspend fun setStatus(id: Long, status: String, updatedAt: Long)

    // --- Survey ID allocation -------------------------------------------------

    @Query("SELECT last_value FROM survey_sequence WHERE year = :year")
    abstract suspend fun sequenceFor(year: Int): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSequence(sequence: SurveySequenceEntity)

    @Query("SELECT survey_id FROM surveys WHERE survey_id = :surveyId LIMIT 1")
    abstract suspend fun findSurveyId(surveyId: String): String?

    /**
     * Allocates the next free Survey ID for [year] and inserts [template] with it, atomically.
     *
     * The loop guards against a sequence row that has drifted behind the actual records
     * (for instance after a restore from backup) so a duplicate ID can never be produced.
     *
     * @return the row id of the inserted record.
     */
    @Transaction
    open suspend fun insertWithNewSurveyId(year: Int, template: SurveyEntity): Long {
        var next = (sequenceFor(year) ?: 0) + 1
        var candidate = formatSurveyId(year, next)
        while (findSurveyId(candidate) != null) {
            next++
            candidate = formatSurveyId(year, next)
        }
        upsertSequence(SurveySequenceEntity(year = year, lastValue = next))
        return insert(template.copy(surveyId = candidate))
    }

    companion object {
        fun formatSurveyId(year: Int, sequence: Int): String =
            "SBT-%04d-%04d".format(year, sequence)
    }
}
