package com.kvk.leh.seabuckthorn.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Fruit quality, juice/lab parameters and yield attributes — one row per survey. */
@Entity(
    tableName = "fruit_quality",
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
data class FruitQualityEntity(
    @PrimaryKey val surveyId: String,
    val fruitColor: String?,
    val fruitColorOther: String?,
    val fruitFirmness: String?,
    val fruitShape: String?,
    val fruitShapeOther: String?,
    val berriesPerCluster: Double?,
    // Primary required quality field.
    val tssBrix: Double?,
    val ph: Double?,
    val juiceYieldPercent: Double?,
    val titratableAcidityPercent: Double?,
    val vitaminCMgPer100g: Double?,
    val totalCarotenoidsMgPer100g: Double?,
    val otherLabParams: String?,
    val fruitsPerBranch: Double?,
    val fruitsPerCluster: Double?,
    val estimatedYieldKgPerShrub: Double?,
    val fruitBearingBranchPercent: Double?,
    val berryColorIntensity: String?,
    val berryDetachmentEase: String?,
    val fruitDamagePercent: Double?
)
