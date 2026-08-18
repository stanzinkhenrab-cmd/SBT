package com.kvkleh.sbtsurvey.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One field survey record.
 *
 * A record is created as soon as the surveyor taps "New Survey" and is written to disk
 * continuously while the form is filled in ([status] = "draft"). It becomes "completed"
 * only when Save & Finish is pressed, which means an interrupted survey is always
 * recoverable from the database.
 *
 * Nullable columns are genuinely optional in the field; non-null columns always carry a
 * value (possibly an empty string) so exports never have to special-case them.
 */
@Entity(
    tableName = "surveys",
    indices = [
        Index(value = ["survey_id"], unique = true),
        Index(value = ["status"]),
        Index(value = ["created_at"]),
        Index(value = ["district"])
    ]
)
data class SurveyEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    /** Human readable, permanently stable identifier, e.g. "SBT-2026-0001". */
    @ColumnInfo(name = "survey_id")
    val surveyId: String,

    // --- Surveyor information -------------------------------------------------
    @ColumnInfo(name = "surveyor_name") val surveyorName: String = "",
    @ColumnInfo(name = "designation") val designation: String = "",
    @ColumnInfo(name = "organization") val organization: String = DEFAULT_ORGANIZATION,

    // --- Date / time of the observation ---------------------------------------
    /** Local calendar date of the survey, ISO-8601 (yyyy-MM-dd). */
    @ColumnInfo(name = "date") val date: String = "",
    /** Local wall-clock time of the survey, 24 h (HH:mm). */
    @ColumnInfo(name = "time") val time: String = "",

    // --- Location -------------------------------------------------------------
    @ColumnInfo(name = "district") val district: String = "",
    @ColumnInfo(name = "block") val block: String = "",
    @ColumnInfo(name = "village") val village: String = "",
    @ColumnInfo(name = "site") val site: String = "",

    // --- Photograph -----------------------------------------------------------
    /** Absolute path inside app-private storage. Never a content:// URI. */
    @ColumnInfo(name = "photo_path") val photoPath: String? = null,
    @ColumnInfo(name = "photo_file_name") val photoFileName: String? = null,
    /** Epoch millis at which the photograph was captured or imported. */
    @ColumnInfo(name = "photo_captured_at") val photoCapturedAt: Long? = null,

    // --- GPS ------------------------------------------------------------------
    @ColumnInfo(name = "latitude") val latitude: Double? = null,
    @ColumnInfo(name = "longitude") val longitude: Double? = null,
    @ColumnInfo(name = "altitude") val altitude: Double? = null,
    /** Horizontal accuracy in metres as reported by the fused location provider. */
    @ColumnInfo(name = "gps_accuracy") val gpsAccuracy: Float? = null,
    /** Epoch millis at which the retained fix was obtained. */
    @ColumnInfo(name = "gps_timestamp") val gpsTimestamp: Long? = null,

    // --- Plant characteristics ------------------------------------------------
    @ColumnInfo(name = "shrub_type") val shrubType: String? = null,
    @ColumnInfo(name = "plant_height") val plantHeight: Double? = null,
    @ColumnInfo(name = "plant_height_unit") val plantHeightUnit: String = "m",

    // --- Fruit ----------------------------------------------------------------
    @ColumnInfo(name = "maturity_stage") val maturityStage: String? = null,
    /** ISO-8601 date (yyyy-MM-dd); null while harvesting has not occurred. */
    @ColumnInfo(name = "harvest_date") val harvestDate: String? = null,
    @ColumnInfo(name = "berry_diameter") val berryDiameter: Double? = null,
    @ColumnInfo(name = "tss_brix") val tssBrix: Double? = null,
    @ColumnInfo(name = "ease_of_harvest") val easeOfHarvest: String? = null,
    @ColumnInfo(name = "fruit_shape") val fruitShape: String? = null,
    @ColumnInfo(name = "fruit_shape_other") val fruitShapeOther: String? = null,

    // --- Record bookkeeping ---------------------------------------------------
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    /** "draft" while the form is open, "completed" after Save & Finish. */
    @ColumnInfo(name = "status") val status: String = "draft"
) {
    val hasGps: Boolean get() = latitude != null && longitude != null

    companion object {
        const val DEFAULT_ORGANIZATION = "Krishi Vigyan Kendra – Leh"
    }
}
