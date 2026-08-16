package com.kvkleh.sbtsurvey.data.prefs

import android.content.Context
import com.kvkleh.sbtsurvey.data.SurveyOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The surveyor details that are carried over from one survey to the next. */
data class SurveyorProfile(
    val name: String = "",
    val designation: String = "",
    val organization: String = SurveyOptions.DEFAULT_ORGANIZATION
) {
    val isComplete: Boolean
        get() = name.isNotBlank() && designation.isNotBlank() && organization.isNotBlank()
}

/** The location a surveyor last worked in, pre-filled on the next record. */
data class LastLocation(
    val district: String = "",
    val block: String = "",
    val village: String = "",
    val site: String = ""
)

/**
 * Small, synchronous preference store. Surveyor identity and the last district or
 * block are re-used across records so that a surveyor walking a plantation only
 * types what actually changes.
 */
class SurveyPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("sbt_survey_prefs", Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(readProfile())
    val profile: StateFlow<SurveyorProfile> = _profile.asStateFlow()

    private val _lastLocation = MutableStateFlow(readLastLocation())
    val lastLocation: StateFlow<LastLocation> = _lastLocation.asStateFlow()

    private fun readProfile() = SurveyorProfile(
        name = prefs.getString(KEY_NAME, "").orEmpty(),
        designation = prefs.getString(KEY_DESIGNATION, "").orEmpty(),
        organization = prefs.getString(KEY_ORGANIZATION, SurveyOptions.DEFAULT_ORGANIZATION)
            .orEmpty()
            .ifBlank { SurveyOptions.DEFAULT_ORGANIZATION }
    )

    private fun readLastLocation() = LastLocation(
        district = prefs.getString(KEY_DISTRICT, "").orEmpty(),
        block = prefs.getString(KEY_BLOCK, "").orEmpty(),
        village = prefs.getString(KEY_VILLAGE, "").orEmpty(),
        site = prefs.getString(KEY_SITE, "").orEmpty()
    )

    fun saveProfile(profile: SurveyorProfile) {
        prefs.edit()
            .putString(KEY_NAME, profile.name)
            .putString(KEY_DESIGNATION, profile.designation)
            .putString(KEY_ORGANIZATION, profile.organization)
            .apply()
        _profile.value = profile
    }

    fun saveLastLocation(location: LastLocation) {
        prefs.edit()
            .putString(KEY_DISTRICT, location.district)
            .putString(KEY_BLOCK, location.block)
            .putString(KEY_VILLAGE, location.village)
            .putString(KEY_SITE, location.site)
            .apply()
        _lastLocation.value = location
    }

    private companion object {
        const val KEY_NAME = "surveyor_name"
        const val KEY_DESIGNATION = "surveyor_designation"
        const val KEY_ORGANIZATION = "surveyor_organization"
        const val KEY_DISTRICT = "last_district"
        const val KEY_BLOCK = "last_block"
        const val KEY_VILLAGE = "last_village"
        const val KEY_SITE = "last_site"
    }
}
