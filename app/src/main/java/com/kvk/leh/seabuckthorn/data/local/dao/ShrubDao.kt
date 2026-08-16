package com.kvk.leh.seabuckthorn.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kvk.leh.seabuckthorn.data.local.entity.ShrubEntity

@Dao
interface ShrubDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(shrub: ShrubEntity)

    @Query("SELECT * FROM shrub_characteristics WHERE surveyId = :surveyId")
    suspend fun getBySurveyId(surveyId: String): ShrubEntity?

    @Query("SELECT AVG(canopyAreaM2) FROM shrub_characteristics WHERE canopyAreaM2 IS NOT NULL")
    suspend fun averageCanopyAreaM2(): Double?
}
