package com.kvk.leh.seabuckthorn.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The root record of a survey. One row per field visit to a shrub/site.
 * [id] is a stable UUID used as the foreign key target for every child table;
 * [surveyCode] is the human-readable identifier (e.g. SBT-2026-0001) shown in the UI and exports.
 */
@Entity(tableName = "surveys")
data class SurveyEntity(
    @PrimaryKey val id: String,
    val surveyCode: String,
    val createdAt: Long,
    val updatedAt: Long,
    val surveyDateEpochDay: Long,
    val surveyTime: String,
    val surveyorName: String,
    val district: String,
    val block: String,
    val village: String,
    val siteName: String,
    val landUseType: String,
    val landUseOther: String?,
    val ownershipStatus: String,
    // Automatically captured location — nullable because GPS may be unavailable in the field.
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val gpsAccuracyM: Float?,
    val gpsAcquiredAt: Long?,
    val gpsStatus: String,
    val remarks: String?,
    val isDraft: Boolean
)
