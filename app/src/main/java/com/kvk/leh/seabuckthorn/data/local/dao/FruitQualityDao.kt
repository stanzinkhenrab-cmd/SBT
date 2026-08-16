package com.kvk.leh.seabuckthorn.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kvk.leh.seabuckthorn.data.local.entity.FruitQualityEntity

@Dao
interface FruitQualityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(fruitQuality: FruitQualityEntity)

    @Query("SELECT * FROM fruit_quality WHERE surveyId = :surveyId")
    suspend fun getBySurveyId(surveyId: String): FruitQualityEntity?

    @Query("SELECT AVG(tssBrix) FROM fruit_quality WHERE tssBrix IS NOT NULL")
    suspend fun averageTss(): Double?

    @Query("SELECT tssBrix FROM fruit_quality WHERE tssBrix IS NOT NULL")
    suspend fun allTssValues(): List<Double>
}
