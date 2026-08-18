package com.kvkleh.sbtsurvey.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Monotonic per-year counter backing the human readable Survey ID.
 *
 * Kept in the database (rather than in preferences) so that allocating an ID and inserting
 * the record it belongs to happen inside a single transaction. That is what guarantees
 * "no duplicate Survey IDs" even if the app is killed mid-save.
 */
@Entity(tableName = "survey_sequence")
data class SurveySequenceEntity(
    @PrimaryKey
    @ColumnInfo(name = "year")
    val year: Int,

    /** Highest sequence number handed out for [year]. */
    @ColumnInfo(name = "last_value")
    val lastValue: Int
)
