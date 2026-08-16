package com.kvk.leh.seabuckthorn.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kvk.leh.seabuckthorn.data.local.entity.EnvironmentEntity

@Dao
interface EnvironmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(environment: EnvironmentEntity)

    @Query("SELECT * FROM environment WHERE surveyId = :surveyId")
    suspend fun getBySurveyId(surveyId: String): EnvironmentEntity?
}
