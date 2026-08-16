package com.kvkleh.sbtsurvey.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single seabuckthorn field observation.
 *
 * Every record starts life as a [STATUS_DRAFT] row that is written to disk as
 * soon as the surveyor opens the form, and is promoted to [STATUS_SAVED] on the
 * review screen. Nothing the surveyor types lives only in memory.
 */
@Entity(
    tableName = "surveys",
    indices = [Index(value = ["surveyId"], unique = true), Index(value = ["status"])]
)
data class SurveyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** Human readable identifier, e.g. `SBT-2026-0001`. Unique for the lifetime of the install. */
    val surveyId: String = "",

    val status: String = STATUS_DRAFT,

    /** Creation instant, captured automatically. Doubles as the survey date and time. */
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /** Instant the record was promoted from draft to saved, `null` while still a draft. */
    val savedAt: Long? = null,

    // 0. Surveyor -----------------------------------------------------------
    val surveyorName: String = "",
    val designation: String = "",
    val organization: String = "",

    // 2. Location -----------------------------------------------------------
    val district: String = "",
    val block: String = "",
    val village: String = "",
    val site: String = "",

    // 3. Photo --------------------------------------------------------------
    /** Absolute path of the JPEG on internal storage, `null` until a photo is taken. */
    val photoPath: String? = null,
    /** File name, always `<surveyId>.jpg`, so exports can be matched to the photo folder. */
    val photoFileName: String? = null,

    // 4. GPS ----------------------------------------------------------------
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Altitude above the WGS-84 ellipsoid, in metres. */
    val altitude: Double? = null,
    val accuracyM: Double? = null,
    val locationCapturedAt: Long? = null,

    // 5. Shrub type ---------------------------------------------------------
    val shrubType: String? = null,

    // 6. Plant height -------------------------------------------------------
    val plantHeight: Double? = null,
    val plantHeightUnit: String = UNIT_METRE,

    // 7. Maturity -----------------------------------------------------------
    val maturityStage: String? = null,
    /** Harvest date at local midnight, `null` when not recorded. */
    val harvestDate: Long? = null,

    // 8. Berry characteristics ---------------------------------------------
    val berryDiameterMm: Double? = null,
    val tssBrix: Double? = null,

    // 9. Ease of harvest ----------------------------------------------------
    val easeOfHarvest: String? = null
) {
    val isDraft: Boolean get() = status == STATUS_DRAFT
    val hasLocation: Boolean get() = latitude != null && longitude != null

    companion object {
        const val STATUS_DRAFT = "DRAFT"
        const val STATUS_SAVED = "SAVED"

        const val UNIT_METRE = "m"
        const val UNIT_FEET = "ft"
    }
}

/**
 * Per-year running number behind [SurveyEntity.surveyId]. Kept in its own table so
 * that allocating an identifier and inserting the record happen in one transaction.
 */
@Entity(tableName = "id_counter")
data class IdCounterEntity(
    @PrimaryKey val year: Int,
    val lastSequence: Int
)
