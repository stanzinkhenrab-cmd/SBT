package com.kvk.leh.seabuckthorn.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kvk.leh.seabuckthorn.data.local.entity.SurveyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SurveyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(survey: SurveyEntity)

    @Update
    suspend fun update(survey: SurveyEntity)

    @Delete
    suspend fun delete(survey: SurveyEntity)

    @Query("DELETE FROM surveys WHERE id = :surveyId")
    suspend fun deleteById(surveyId: String)

    @Query("SELECT * FROM surveys WHERE id = :surveyId")
    suspend fun getById(surveyId: String): SurveyEntity?

    @Query("SELECT * FROM surveys WHERE id = :surveyId")
    fun observeById(surveyId: String): Flow<SurveyEntity?>

    @Query("SELECT * FROM surveys ORDER BY surveyDateEpochDay DESC, createdAt DESC")
    fun observeAll(): Flow<List<SurveyEntity>>

    @Query(
        """
        SELECT surveys.* FROM surveys
        LEFT JOIN phenology ON phenology.surveyId = surveys.id
        WHERE (:query = '' OR surveys.surveyCode LIKE '%' || :query || '%'
               OR surveys.village LIKE '%' || :query || '%'
               OR surveys.siteName LIKE '%' || :query || '%'
               OR surveys.surveyorName LIKE '%' || :query || '%')
          AND (:village = '' OR surveys.village = :village)
          AND (:surveyor = '' OR surveys.surveyorName = :surveyor)
          AND (:maturityStage = '' OR phenology.dominantMaturityStage = :maturityStage)
          AND (:fromEpochDay IS NULL OR surveys.surveyDateEpochDay >= :fromEpochDay)
          AND (:toEpochDay IS NULL OR surveys.surveyDateEpochDay <= :toEpochDay)
        ORDER BY surveys.surveyDateEpochDay DESC, surveys.createdAt DESC
        """
    )
    fun search(
        query: String,
        village: String,
        surveyor: String,
        maturityStage: String,
        fromEpochDay: Long?,
        toEpochDay: Long?
    ): Flow<List<SurveyEntity>>

    @Query("SELECT DISTINCT village FROM surveys WHERE village != '' ORDER BY village")
    fun observeDistinctVillages(): Flow<List<String>>

    @Query("SELECT DISTINCT surveyorName FROM surveys WHERE surveyorName != '' ORDER BY surveyorName")
    fun observeDistinctSurveyors(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM surveys WHERE isDraft = 0")
    suspend fun countCompleted(): Int

    @Query("SELECT COUNT(DISTINCT village) FROM surveys WHERE isDraft = 0 AND village != ''")
    suspend fun countDistinctVillages(): Int

    @Query("SELECT surveyCode FROM surveys WHERE surveyCode LIKE :prefix || '%' ORDER BY surveyCode DESC LIMIT 1")
    suspend fun getLastSurveyCodeForPrefix(prefix: String): String?

    @Query("SELECT * FROM surveys WHERE isDraft = 0 ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatest(): SurveyEntity?

    @Query("SELECT * FROM surveys WHERE isDraft = 0 AND latitude IS NOT NULL AND longitude IS NOT NULL")
    suspend fun getAllWithGps(): List<SurveyEntity>

    @Query("SELECT * FROM surveys WHERE isDraft = 0 AND latitude IS NOT NULL AND longitude IS NOT NULL ORDER BY surveyDateEpochDay DESC")
    fun observeAllWithGps(): Flow<List<SurveyEntity>>

    @Query("SELECT * FROM surveys WHERE isDraft = 0")
    suspend fun getAllCompleted(): List<SurveyEntity>
}
