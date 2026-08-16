package com.kvk.leh.seabuckthorn.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kvk.leh.seabuckthorn.data.local.entity.BerryMeasurementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BerryMeasurementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(berries: List<BerryMeasurementEntity>)

    @Query("DELETE FROM berry_measurements WHERE surveyId = :surveyId")
    suspend fun deleteBySurveyId(surveyId: String)

    @Query("SELECT * FROM berry_measurements WHERE surveyId = :surveyId ORDER BY berryIndex")
    suspend fun getBySurveyId(surveyId: String): List<BerryMeasurementEntity>

    @Query("SELECT * FROM berry_measurements WHERE surveyId = :surveyId ORDER BY berryIndex")
    fun observeBySurveyId(surveyId: String): Flow<List<BerryMeasurementEntity>>

    @Query("SELECT lengthMm FROM berry_measurements WHERE lengthMm IS NOT NULL")
    suspend fun allLengths(): List<Double>

    @Query("SELECT widthMm FROM berry_measurements WHERE widthMm IS NOT NULL")
    suspend fun allWidths(): List<Double>

    @Query("SELECT weightG FROM berry_measurements WHERE weightG IS NOT NULL")
    suspend fun allWeights(): List<Double>
}
