package com.kvk.leh.seabuckthorn.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Shrub morphology, size, stand and health characteristics — one row per survey. */
@Entity(
    tableName = "shrub_characteristics",
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
data class ShrubEntity(
    @PrimaryKey val surveyId: String,
    val shrubType: String,
    val growthForm: String,
    val plantHeightM: Double?,
    val canopyNsM: Double?,
    val canopyEwM: Double?,
    // Computed from canopyNsM/canopyEwM at save time — see CanopyCalculations.
    val avgCanopyDiameterM: Double?,
    val canopyAreaM2: Double?,
    val stemCircumferenceCm: Double?,
    val numMajorStems: Int?,
    val estimatedAgeYears: Double?,
    val ageEstimationMethod: String?,
    val plantDensityPerHa: Double?,
    val approxSpacingM: Double?,
    val regenerationStatus: String?,
    val presenceOfSuckers: Boolean,
    val floweringStatus: Boolean,
    val fruitingStatus: Boolean,
    val numBranches: Int?,
    val mainStemDiameterCm: Double?,
    val branchDiameterCm: Double?,
    val leafColor: String?,
    val leafDensity: String?,
    val canopyCoverPercent: Double?,
    val thornDensity: String?,
    val suckerAbundance: String?,
    val overallHealth: String?,
    val pestIncidence: String?,
    val diseaseIncidence: String?,
    val browsingDamage: String?,
    val mechanicalDamage: String?,
    val droughtStress: String?,
    val otherStressSymptoms: String?
)
