package com.kvk.leh.seabuckthorn.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kvk.leh.seabuckthorn.data.local.entity.PhenologyEntity

@Dao
interface PhenologyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(phenology: PhenologyEntity)

    @Query("SELECT * FROM phenology WHERE surveyId = :surveyId")
    suspend fun getBySurveyId(surveyId: String): PhenologyEntity?

    @Query("SELECT COUNT(*) FROM phenology WHERE dominantMaturityStage = :stage")
    suspend fun countByStage(stage: String): Int
}
