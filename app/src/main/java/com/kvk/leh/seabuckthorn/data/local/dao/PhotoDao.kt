package com.kvk.leh.seabuckthorn.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kvk.leh.seabuckthorn.data.local.entity.PhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: PhotoEntity): Long

    @Query("DELETE FROM photos WHERE id = :photoId")
    suspend fun deleteById(photoId: Long)

    @Query("SELECT * FROM photos WHERE surveyId = :surveyId ORDER BY photoNumber")
    fun observeBySurveyId(surveyId: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE surveyId = :surveyId ORDER BY photoNumber")
    suspend fun getBySurveyId(surveyId: String): List<PhotoEntity>

    @Query("SELECT COUNT(*) FROM photos WHERE surveyId = :surveyId")
    suspend fun countForSurvey(surveyId: String): Int

    @Query("SELECT MAX(photoNumber) FROM photos WHERE surveyId = :surveyId")
    suspend fun maxPhotoNumber(surveyId: String): Int?

    @Query("SELECT * FROM photos ORDER BY capturedAt DESC")
    suspend fun getAll(): List<PhotoEntity>
}
