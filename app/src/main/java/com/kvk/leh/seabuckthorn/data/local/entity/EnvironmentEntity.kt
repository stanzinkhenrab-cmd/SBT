package com.kvk.leh.seabuckthorn.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Site and environmental context — slope, aspect, soil, water source, land-use history. */
@Entity(
    tableName = "environment",
    foreignKeys = [
        ForeignKey(
            entity = SurveyEntity::class,
            parentColumns = ["id"],
            childColumns = ["surveyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("surveyId", unique = true)]
)
data class EnvironmentEntity(
    @PrimaryKey val surveyId: String,
    val slopePercent: Double?,
    val aspect: String?,
    val soilSurfaceCondition: String?,
    val soilMoistureClass: String?,
    val waterRegime: String?,
    val distanceFromWaterM: Double?,
    val associatedVegetation: String?,
    val grazingIntensity: String?,
    val riverName: String?,
    val elevationZone: String?,
    val terrainType: String?,
    val soilType: String?,
    val soilSurfaceTexture: String?,
    val landUseHistory: String?
)
