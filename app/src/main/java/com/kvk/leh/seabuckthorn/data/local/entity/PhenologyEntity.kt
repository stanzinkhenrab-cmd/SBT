package com.kvk.leh.seabuckthorn.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Fruit maturity distribution and phenological date milestones for a survey.
 * Every date field is paired with an `*Estimated` flag: false = "date observed" in the field,
 * true = "date estimated" (surveyor was not present at the exact stage).
 */
@Entity(
    tableName = "phenology",
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
data class PhenologyEntity(
    @PrimaryKey val surveyId: String,
    val dominantMaturityStage: String?,
    val pctUnripe: Double?,
    val pctIntermediate: Double?,
    val pctRipe: Double?,
    val pctOverripe: Double?,

    val floweringInitiationDate: Long?,
    val floweringInitiationEstimated: Boolean,
    val floweringPeakDate: Long?,
    val floweringPeakEstimated: Boolean,
    val fruitSetDate: Long?,
    val fruitSetEstimated: Boolean,
    val fruitDevelopmentInitiationDate: Long?,
    val fruitDevelopmentInitiationEstimated: Boolean,
    val firstColorChangeDate: Long?,
    val firstColorChangeEstimated: Boolean,
    val firstMaturityDate: Long?,
    val firstMaturityEstimated: Boolean,
    val fiftyPercentMaturityDate: Long?,
    val fiftyPercentMaturityEstimated: Boolean,
    val peakMaturityDate: Long?,
    val peakMaturityEstimated: Boolean,
    val harvestInitiationDate: Long?,
    val harvestInitiationEstimated: Boolean,
    val estimatedFullMaturityDate: Long?,
    val estimatedFullMaturityEstimated: Boolean
)
