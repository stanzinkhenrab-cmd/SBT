package com.kvk.leh.seabuckthorn.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A photograph captured for a survey, tagged with its own capture-time GPS/date/category. */
@Entity(
    tableName = "photos",
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
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surveyId: String,
    val filePath: String,
    val category: String,
    val photoNumber: Int,
    val capturedAt: Long,
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val isWatermarked: Boolean
)
