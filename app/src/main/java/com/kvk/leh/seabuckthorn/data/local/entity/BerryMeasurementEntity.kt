package com.kvk.leh.seabuckthorn.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One measured berry (Berry 1..N) within a survey's fruit sample. */
@Entity(
    tableName = "berry_measurements",
    foreignKeys = [
        ForeignKey(
            entity = SurveyEntity::class,
            parentColumns = ["id"],
            childColumns = ["surveyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("surveyId")]
)
data class BerryMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surveyId: String,
    val berryIndex: Int,
    val lengthMm: Double?,
    val widthMm: Double?,
    val weightG: Double?
)
